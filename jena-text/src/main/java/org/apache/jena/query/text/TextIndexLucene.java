/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.query.text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.NodeFactory ;
import com.hp.hpl.jena.sparql.util.NodeFactoryExtra;

public class TextIndexLucene implements TextIndex
{
    private static Logger log = LoggerFactory.getLogger(TextIndexLucene.class) ;
    
    private static int MAX_N = 10000 ;
    private static final Version VER = Version.LUCENE_41 ;
    static FieldType ftIndexedStored = TextField.TYPE_STORED ; 
    static FieldType ftIndexed = TextField.TYPE_NOT_STORED ;
    
    private final EntityDefinition docDef ;
    private final Directory directory ;
    private IndexWriter indexWriter ;
    private Analyzer analyzer = new StandardAnalyzer(VER);
    
    
    public TextIndexLucene(Directory directory, EntityDefinition def)
    {
        this.directory = directory ;
        this.docDef = def ;
        
        // force creation of the index if it don't exist
        // othewise if we get a search before data is written we get an exception
        startIndexing();
        finishIndexing();
    }
    
    @Override
    public void startIndexing()
    { 
        try {
            IndexWriterConfig wConfig = new IndexWriterConfig(VER, analyzer) ;
            indexWriter = new IndexWriter(directory, wConfig) ;
        } catch (IOException e) { exception(e) ; }
    }

    @Override
    public void finishIndexing()
    {
        try { indexWriter.commit() ; indexWriter.close() ; indexWriter = null ; }
        catch (IOException e) { exception(e) ; }
    }
    
    @Override
    public void abortIndexing()
    {
        try { indexWriter.rollback() ; }
        catch ( IOException ex) { exception(ex) ; }
    }

    @Override
    public void close()
    { 
        if ( indexWriter != null ) 
            try { indexWriter.close() ; } catch (IOException ex) { exception(ex) ; }
    }

    @Override
    public void addEntity(Entity entity)
    {
        log.info("Add entity: "+entity) ;
        try {
            Document doc = doc(entity) ;
            indexWriter.addDocument(doc) ;
        } catch (IOException e) { exception(e) ; }
    }

    private Document doc(Entity entity)
    {
        Document doc = new Document() ;
        Field entField = new Field(docDef.getEntityField(), entity.getId(), ftIndexedStored) ;
        doc.add(entField) ;
        
        for ( Entry<String, Object> e : entity.getMap().entrySet() )
        {
            Field field = new Field(e.getKey(), (String)e.getValue(), ftIndexed) ;
            doc.add(field) ;
        }
        return doc ;
    }

    @Override
    public Map<String, Node> get(String uri)
    {
        try {
            IndexReader indexReader = DirectoryReader.open(directory) ;
            List<Map<String, Node>> x = get$(indexReader, uri) ;
            if ( x.size() == 0)
                return null ;
//            if ( x.size() > 1)
//                throw new TextIndexException("Multiple entires for "+uri) ;
            return x.get(0) ;
        } catch (Exception ex) { exception(ex) ; return null ; } 
    }
    
    private List<Map<String, Node>> get$(IndexReader indexReader , String uri)  throws ParseException, IOException {
        String escaped = QueryParser.escape(uri);
        String qs = docDef.getEntityField()+":"+escaped ;
        QueryParser queryParser = new QueryParser(VER, docDef.getPrimaryField(), analyzer);
        Query query = queryParser.parse(qs);
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        ScoreDoc[] sDocs = indexSearcher.search(query, 1).scoreDocs ;   // Only need one hit.    
        List<Map<String, Node>> records = new ArrayList<Map<String, Node>>() ;

        // Align and DRY with Solr.
        for ( ScoreDoc sd : sDocs )
        {
            Document doc = indexSearcher.doc(sd.doc) ;
            String[] x = doc.getValues(docDef.getEntityField()) ;
            if ( x.length != 1 )
            {}
            String uriStr = x[0] ;
            Map<String, Node> record = new HashMap<String, Node>() ; 
            Node entity = NodeFactory.createURI(uriStr) ;
            record.put(docDef.getEntityField(), entity) ;
                    
            for ( String f : docDef.fields() )
            {
                //log.info("Field: "+f) ;
                String[] values = doc.getValues(f) ;
                for ( String v : values )
                {
                    Node n = entryToNode(v) ;
                    record.put(f, n) ;
                }
                records.add(record) ;
            }
        }
        return records ;
    }


    @Override
    public List<Node> query(String qs) { return query(qs, MAX_N) ; } 
    
    @Override
    public List<Node> query(String qs, int limit)
    {
        try {
            // Upgrade at Java7 ... 
            IndexReader indexReader = DirectoryReader.open(directory) ;
            try { return query$(indexReader, qs, limit) ; } 
            finally { indexReader.close() ; }
        } catch (Exception ex) { exception(ex) ; return null ; } 
    }
        
    public List<Node> query$(IndexReader indexReader , String qs, int limit) throws ParseException, IOException {
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        QueryParser queryParser = new QueryParser(VER, docDef.getPrimaryField(), analyzer);
        Query query = queryParser.parse(qs);
        
        if ( limit <= 0 )
            limit = MAX_N ;
        ScoreDoc[] sDocs = indexSearcher.search(query, limit).scoreDocs ;
        
        List<Node> results = new ArrayList<Node>() ;
        
        // Align and DRY with Solr.
        for ( ScoreDoc sd : sDocs )
        {
            Document doc = indexSearcher.doc(sd.doc) ;
            String[] values = doc.getValues(docDef.getEntityField()) ;
            for ( String v : values )
            {
                Node n = NodeFactory.createURI(v);
                results.add(n) ;
            }
        }
        return results ;
    }

    
    @Override
    public EntityDefinition getDocDef()
    {
        return docDef ;
    }

    private Node entryToNode(String v)
    {
        // TEMP
        return NodeFactoryExtra.createLiteralNode(v, null, null) ;
    }

    private static void exception(Exception ex)
    {
        throw new TextIndexException(ex) ;
    }
}

