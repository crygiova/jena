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

package org.apache.jena.query.text.assembler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.jena.query.text.EntityDefinition;
import org.apache.jena.query.text.TextIndexException;
import org.junit.Test;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.vocabulary.RDFS;

/**
 * Test assembler for EntityMap
 */
public class TestEntityMapAssembler {
	
	private static final String TESTBASE = "http://example.org/test/";
	private static final Resource spec1;
	private static final Resource spec2;
	private static final Resource specNoEntityField;
	private static final Resource specNoDefaultField;
	private static final Resource specNoMapProperty;
	private static final Resource specNoPrimaryFieldDef;
	
	@Test public void EntityHasPrimaryField() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		EntityDefinition entityDef = emAssembler.open(null, spec1, null);
		assertEquals(SPEC1_DEFAULT_FIELD, entityDef.getPrimaryField());
	}
	
	@Test public void EntityHasEntityField() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		EntityDefinition entityDef = emAssembler.open(null, spec1, null);
		assertEquals(SPEC1_ENTITY_FIELD, entityDef.getEntityField());		
	}
	
	@Test public void EntityHasMapEntries() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		EntityDefinition entityDef = emAssembler.open(null, spec1, null);
		assertEquals(entityDef.getPredicate(SPEC1_DEFAULT_FIELD), SPEC1_PREDICATE.asNode());
	}
	
	@Test public void EntityHasMultipleMapEntries() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		EntityDefinition entityDef = emAssembler.open(null, spec2, null);
		assertEquals(entityDef.getPredicate(SPEC2_DEFAULT_FIELD), SPEC2_PREDICATE1.asNode());
		assertEquals(entityDef.getPredicate(SPEC2_FIELD2), SPEC2_PREDICATE2.asNode());
	}
	
	@Test public void errorOnNoEntityField() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		try {
		    emAssembler.open(null, specNoEntityField, null);
		    fail("should throw exception if there is no entity field");
		} catch (IllegalStateException e) {}		
	}
	
	@Test public void errorOnNoDefaultField() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		try {
		    emAssembler.open(null, specNoDefaultField, null);
		    fail("should throw exception if there is no default field");
		} catch (IllegalStateException e) {}		
	}
	
	@Test public void errorOnNoMapProperty() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		try {
		    emAssembler.open(null, specNoMapProperty, null);
		    fail("should throw exception if there is no map property");
		} catch (IllegalStateException e) {}		
	}
	
	@Test public void errorOnNoPrimaryFieldDef() {
		EntityMapAssembler emAssembler = new EntityMapAssembler();
		try {
		    emAssembler.open(null, specNoPrimaryFieldDef, null);
		    fail("should throw exception if there is no map property");
		} catch (TextIndexException e) {
			assertTrue(e.getMessage().contains(SPEC1_DEFAULT_FIELD));
		}	
		
	}
	
	private static final String SPEC1_ENTITY_FIELD = "spec1EntityField";
	private static final String SPEC1_DEFAULT_FIELD = "spec1DefaultField";
	private static final Property SPEC1_PREDICATE = RDFS.label;
	
	private static final String SPEC2_ENTITY_FIELD = "spec2EntityField";
	private static final String SPEC2_DEFAULT_FIELD = "spec2DefaultField";
	private static final String SPEC2_FIELD2 = "spec2Field2";
	private static final Property SPEC2_PREDICATE1 = RDFS.label;
	private static final Property SPEC2_PREDICATE2 = RDFS.comment;
	static {
		Model model = ModelFactory.createDefaultModel();
		
		// create a simple entity map specification
		
		spec1 = model.createResource(TESTBASE + "spec1")
				     .addProperty(TextVocab.pEntityField, SPEC1_ENTITY_FIELD)
				     .addProperty(TextVocab.pDefaultField, SPEC1_DEFAULT_FIELD)
				     .addProperty(TextVocab.pMap,
				    		      model.createList(
				    		    		  new RDFNode[] {
				    		    				model.createResource()
				    		    				     .addProperty(TextVocab.pField, SPEC1_DEFAULT_FIELD)
				    		    				     .addProperty(TextVocab.pPredicate, SPEC1_PREDICATE)
				    		    		  }))
				     ;
		
		// create an entity map specification with multiple map entries
		
		spec2 = model.createResource(TESTBASE + "spec2")
				     .addProperty(TextVocab.pEntityField, SPEC2_ENTITY_FIELD)
				     .addProperty(TextVocab.pDefaultField, SPEC2_DEFAULT_FIELD)
				     .addProperty(TextVocab.pMap,
				    		      model.createList(
				    		    		  new RDFNode[] {
				    		    				model.createResource()
				    		    				     .addProperty(TextVocab.pField, SPEC2_DEFAULT_FIELD)
				    		    				     .addProperty(TextVocab.pPredicate, SPEC2_PREDICATE1),
							    		    	model.createResource()
							    		    	     .addProperty(TextVocab.pField, SPEC2_FIELD2)
							    		    		 .addProperty(TextVocab.pPredicate, SPEC2_PREDICATE2),
				    		    				    
				    		    		  }))
				     ;
		// bad assembler spec
		specNoEntityField = 
				model.createResource(TESTBASE + "specNoEntityField")
				     .addProperty(TextVocab.pDefaultField, SPEC1_DEFAULT_FIELD)
				     .addProperty(TextVocab.pMap,
				    		      model.createList(
				    		    		  new RDFNode[] {
				    		    				model.createResource()
				    		    				     .addProperty(TextVocab.pField, SPEC1_DEFAULT_FIELD)
				    		    				     .addProperty(TextVocab.pPredicate, SPEC1_PREDICATE)
				    		    		  }))
				     ;
		// bad assembler spec
		specNoDefaultField = 
				model.createResource(TESTBASE + "specNoDefaultField")
				     .addProperty(TextVocab.pDefaultField, SPEC1_DEFAULT_FIELD)
				     .addProperty(TextVocab.pMap,
				    		      model.createList(
				    		    		  new RDFNode[] {
				    		    				model.createResource()
				    		    				     .addProperty(TextVocab.pField, SPEC1_DEFAULT_FIELD)
				    		    				     .addProperty(TextVocab.pPredicate, SPEC1_PREDICATE)
				    		    		  }))
				     ;
		// bad assembler spec
		specNoMapProperty = 
				model.createResource(TESTBASE + "specNoMapProperty")
				     .addProperty(TextVocab.pEntityField, SPEC1_ENTITY_FIELD)
				     .addProperty(TextVocab.pDefaultField, SPEC1_DEFAULT_FIELD)
				     ;
		
		// bad assembler spec		
		specNoPrimaryFieldDef =
				model.createResource(TESTBASE + "noPrimaryFieldDef")
				     .addProperty(TextVocab.pEntityField, SPEC1_ENTITY_FIELD)
				     .addProperty(TextVocab.pDefaultField, SPEC1_DEFAULT_FIELD)
				     .addProperty(TextVocab.pMap,
				    		      model.createList(
				    		    		  new RDFNode[] {
				    		    				model.createResource()
				    		    				     .addProperty(TextVocab.pField, SPEC1_ENTITY_FIELD)
				    		    				     .addProperty(TextVocab.pPredicate, SPEC1_PREDICATE)
				    		    		  }))
				     ;
	}
}
