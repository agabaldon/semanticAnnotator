/********************************************************************** 
 * Note: This license has also been called the "New BSD License" or 
 * "Modified BSD License". See also the 2-clause BSD License.
 *
 * Copyright � 2020-2021 - General Electric Company, All Rights Reserved
 * 
 * Projects: GraSEN, developed with the support of the Defense Advanced 
 * Research Projects Agency (DARPA) under Agreement  No. HR00112190017.
 *
 * Redistribution and use in source and binary forms, with or without 
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, 
 *    this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, 
 *    this list of conditions and the following disclaimer in the documentation 
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE 
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE 
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR 
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF 
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS 
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN 
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE.
 *
 ***********************************************************************/
package com.ge.research.sadl.darpa.aske.grfnmodelextractor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.*;

import org.apache.jena.util.iterator.ExtendedIterator;
import org.apache.jena.vocabulary.RDFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ge.research.sadl.builder.ConfigurationManagerForIdeFactory;
import com.ge.research.sadl.builder.IConfigurationManagerForIDE;
import com.ge.research.sadl.darpa.aske.grfnmodelextractor.GrFN_Type.Field;
import com.ge.research.sadl.jena.JenaProcessorException;
//import com.ge.research.sadl.jena.inference.SadlJenaModelGetterPutter;
import com.ge.research.sadl.owl2sadl.OwlImportException;
import com.ge.research.sadl.owl2sadl.OwlToSadl;
import com.ge.research.sadl.processing.SadlConstants;
import com.ge.research.sadl.reasoner.AmbiguousNameException;
import com.ge.research.sadl.reasoner.CircularDependencyException;
import com.ge.research.sadl.reasoner.ConfigurationException;
import com.ge.research.sadl.reasoner.ConfigurationItem;
import com.ge.research.sadl.reasoner.InvalidNameException;
import com.ge.research.sadl.reasoner.QueryCancelledException;
import com.ge.research.sadl.reasoner.QueryParseException;
import com.ge.research.sadl.reasoner.ReasonerNotFoundException;
import com.ge.research.sadl.reasoner.ResultSet;
import com.ge.research.sadl.reasoner.TranslationException;
import com.ge.research.sadl.reasoner.IConfigurationManagerForEditing.Scope;
import com.ge.research.sadl.reasoner.IReasoner;
import com.ge.research.sadl.reasoner.utils.SadlUtils;
import com.google.gson.Gson;

/**
 * @author 212438865
 * Based on the ASKE JavaModelExtractor
 *
 */
public class GrFNModelExtractor  {

//	public static final String EXTRACTED_MODELS_FOLDER_PATH_FRAGMENT = "ExtractedModels";
	public static final String GRFN_EXTRACTION_MODEL_URI = "http://grasen.ge.com/GrFNExtractionModel";
	public static final String GRFN_EXTRACTION_MODEL_PREFIX = "grfnem";
	public static final String SEMANNOTATOR_MODEL_URI = "http://sadl.org/SemAnnotator.sadl";
//	public static final String SEMANNOTATOR_MODEL_PREFIX = "semannotator";

	private static final Logger logger = LoggerFactory.getLogger(GrFNModelExtractor.class);
    
	private String owlModelsFolder;
    
//	private String packageName = "";
//	private AnswerCurationManager curationMgr = null;
//	private Map<String, String> preferences;

	// Assumption: the GrFN meta model and the GrFN model extracted are in the same folder (same kbase)
	private IConfigurationManagerForIDE grFNExtractionModelConfigMgr;	// the ConfigurationManager used to access the grFN extraction model
	private String grFNExtractionModelUri;	// the name of the grFN extraction model

	private String grFNExtractionModelPrefix;	// the prefix of the grFN extraction model

	private OntModel codeModel;  //The model being created
	private Map<String,OntModel> codeModels = null;
	private String codeModelName;	// the name of the model  being created by extraction
	private String codeModelPrefix; // the prefix of the model being created by extraction

	private OntModel inferredModel;
	
	private OntModel aggregateImports;
	
	private ResultSet queryResults;
	
	private String query = "select distinct ?Name ?Qual_name (?Q4 as ?Function_name) (strafter(str(?Annotation),'#') as ?AnnotationType) ?Description ?Loc_start ?Loc_end ?Equation ?SrcLocStart ?SrcLocEnd\n"
			+ "where {\n"
			+ "{ ?Annotation <rdfs:subClassOf>* <ControllerConstruct>\n"
			+ ". ?x a ?Annotation\n"
			+ ".  filter not exists {\n"
			+ "       ?C <rdfs:subClassOf> ?Annotation.\n"
			+ "       ?x a ?C.\n"
			+ "     }\n"
			+ ". ?x <varName> ?Name\n"
			+ ". ?Annotation <description> ?Description\n"
			+ ". ?x <identifier> ?Qual_name\n"
			+ ". FILTER(!regex(str(?Qual_name), 'LOOP')) # maybe there is a more generic way to drop, e.g. from_source is false etc.\n"
			+ "#. LET(?Q2 := replace(str(?Qual_name),'.*\\\\.','')) # why does this give an error?????\n"
			+ ". LET(?Q2 := replace(str(?Qual_name),'[.]','###'))\n"
			+ ". LET(?Q3 := replace(str(?Q2),'.*###',''))\n"
			+ ". LET(?Q4 := replace(str(?Q3),'::.*',''))\n"
			+ ". ?x <metadata> ?md\n"
			+ ". LET(?Loc_start := 1)\n"
			+ ". LET(?Loc_end := 999)\n"
			+ ". OPTIONAL{?md <line_begin> ?SrcLocStart}\n"
			+ ". OPTIONAL{?md <line_end> ?SrcLocEnd}\n"
			+ ". OPTIONAL{?x <semanticExpression> ?Equation}\n"
			+ "} union {\n"
			+ "?sg <rdf:type> <SubGraph>\n"
			+ ". ?sg <nodes> ?x\n"
			+ ". ?x <rdf:type> <Controller>\n"
			+ ". ?x <constructName> ?Name\n"
			+ ". LET(?Q2 := replace(str(?Name),'[.]','###'))\n"
			+ ". LET(?Q4 := replace(str(?Q2),'.*###',''))\n"
			+ "#. ?x <rdfs:subClassOf> <Controller>\n"
			+ ". ?x a ?Annotation.\n"
			+ "  filter not exists {\n"
			+ "       ?C <rdfs:subClassOf> ?Annotation.\n"
			+ "       ?x a ?C.\n"
			+ "     }\n"
			+ "  ?Annotation <rdfs:subClassOf>* <ControllerConstruct>\n"
			+ ". LET(?Loc_start := 1)\n"
			+ ". LET(?Loc_end := 999)\n"
			+ ". OPTIONAL{?x <linebegin> ?SrcLocStart.}\n"
			+ ". OPTIONAL{?x <lineend> ?SrcLocEnd.}\n"
			+ ". ?Annotation <description> ?Description\n"
			+ ". OPTIONAL{?x <lambda> ?Eq . \n"
			+ "    LET(?Equation := replace(str(?Eq),'^.*: ',''))}\n"
			+ "} union { # get the ControlConstructs that are identified inline\n"
			+ "?nd <rdf:type> <ControllerConstruct>\n"
			+ ". ?fun <nodes> ?nd \n"
			+ ". ?fun <lambda> ?Eq\n"
			+ ". ?nd <rdf:type> ?Annotation\n"
			+ ". ?Annotation <rdfs:subClassOf> <ControllerConstruct>\n"
			+ ". ?Annotation <description> ?Description .\n"
			+ "filter not exists{?nd a <Variable>.}\n"
			+ " LET(?Equation := replace(str(?Eq),'^.*: ',''))\n"
			+ ". LET(?Loc_start := 1)\n"
			+ ". LET(?Loc_end := 999)\n"
			+ ". ?fun <metadata> ?md\n"
			+ ". OPTIONAL{?md <line_begin> ?SrcLocStart}\n"
			+ ". OPTIONAL{?md <line_end> ?SrcLocEnd}\n"
			+ ". ?he <rdf:type> <HyperEdge> \n"
			+ ". ?he <function> ?fun\n"
			+ ". ?he <outputs> ?out . ?out <identifier> ?Qual_name\n"
			+ "#. LET(?Function_name := 'TBD')\n"
			+ " \n"
			+ "#. ?x <identifier> ?Qual_name\n"
			+ ". FILTER(!regex(str(?Qual_name), 'LOOP')) # maybe there is a more generic way to drop, e.g. from_source is false etc.\n"
			+ "#. LET(?Q2 := replace(str(?Qual_name),'.*\\\\.','')) # why does this give an error?????\n"
			+ ". LET(?Q2 := replace(str(?Qual_name),'[.]','###'))\n"
			+ ". LET(?Q3 := replace(str(?Q2),'.*###',''))\n"
			+ ". LET(?Q4 := replace(str(?Q3),'::.*',''))\n"
			+ "}\n"
			+ "} order by ?AnnotationType ?Name ?SrcLocStart";
	
//	private Individual rootContainingInstance = null;

//	private Map<Range, MethodCallMapping> postProcessingList = new HashMap<Range, MethodCallMapping>(); // key is the MethodCallExpr
//	Map<String, String> classNameMap = new HashMap<String, String>();

	
	public GrFNModelExtractor() {
	}
	
//	private void setCurationMgr(AnswerCurationManager curationMgr) {
//		this.curationMgr = curationMgr;
//	}
//	private AnswerCurationManager getCurationMgr() {
//		return curationMgr;
//	}
//	private void setPreferences(Map<String, String> preferences) {
//		this.preferences = preferences;
//	}
	
	public String writeResultsToFile(String filename)  throws IOException, OwlImportException {
		String fullpathfilename = getOwlModelsFolder() + File.separator + filename;
        File csvf = new File(fullpathfilename);
       	if (csvf.exists()) {
       		csvf.delete();
    	}
    	if (!csvf.exists()) {
    		csvf.createNewFile();
    	}
    	if (!csvf.canWrite()) {
    		throw new IllegalArgumentException("File cannot be written: " + csvf);
    	}
    	//declared here only to make visible to finally clause; generic reference
    	Writer output = null;
    	try {
    		//use buffering
    		//FileWriter always assumes default encoding is OK!
    		output = new BufferedWriter( new FileWriter(csvf) );
    		if(queryResults != null)
    			output.write(queryResults.toString());
    	}
    	finally {
    		//flush and close both "output" and its underlying FileWriter
    		if (output != null) output.close();
    	}
		
		return fullpathfilename;
	}
	
	private void initializeContent(String modelName, String modelPrefix) {
//		packageName = "";
//		postProcessingList.clear();
		if (getCodeModelName() == null) {	// don't override a preset model name
			setCodeModelName(modelName);
		}
		if (getCodeModelPrefix() == null) {
			setCodeModelPrefix(modelPrefix);	// don't override a preset model name		
		}
		codeModel = null;
//		rootContainingInstance = null;
//		postProcessingList.clear();
//		classNameMap.clear();
//		clearAggregatedComments();

	}
	
	/***
	 * inputIdentifier: identifier of file with grFN to be parsed
	 * content: string with the full content of the file
	 * modelName: url of the output model name
	 * modelPrefix
	 * @throws ReasonerNotFoundException 
	 * @throws TranslationException 
	 * @throws QueryCancelledException 
	 * @throws QueryParseException 
	 * @throws AmbiguousNameException 
	 * @throws InvalidNameException 
	 */
//	@Override
	public boolean process(String inputIdentifier, String content, String modelName, String modelPrefix, int requestType)
			throws ConfigurationException, IOException, ReasonerNotFoundException, TranslationException, QueryParseException, QueryCancelledException, InvalidNameException, AmbiguousNameException {

		initializeContent(modelName, modelPrefix);

//	    if (getCodeModelConfigMgr() != null) {
//			getCodeModelConfigMgr().clearReasoner(); //nulls reasoner and translator
//	    }
	    
//      initializeGrFNModel(getCurationMgr().getOwlModelsFolder());
	    initializeGrFNModel(getOwlModelsFolder());
	    
//		parse(inputIdentifier, getCurationMgr().getOwlModelsFolder(), content);
		parse(inputIdentifier, getOwlModelsFolder(), content);

//		getCurrentCodeModel().write(System.out, "N3");
		
		//TODO: invoke the reasoner on the rules.
		
//		Reasoner reasoner = getCurrentCodeModel().getReasoner();
		
//		final String format = SadlSerializationFormat.RDF_XML_ABBREV_FORMAT;
//		configMgr = ConfigurationManagerForIdeFactory.getConfigurationManagerForIDE(getOwlModelsFolder(), format);
//		configMgr.clearReasoner();
		
//		Reasoner reasoner = getCurrentCodeModel().getReasoner();
		
//		getCodeModelConfigMgr().setReasonerClassName("com.naturalsemanticsllc.sadl.reasoner.JenaAugmentedReasonerPlugin");


//		//TODO: working on this

		if (requestType > 0) {
		
			getCodeModelConfigMgr().clearReasoner(); //nulls reasoner and translator
			
			IReasoner reasoner = getCodeModelConfigMgr().getReasoner();
			
	//		reasoner.reset(); //keeps reasoner and nulls assoc. models
	
			if (!reasoner.isInitialized()) {
				reasoner.setConfigurationManager(getCodeModelConfigMgr());
				List<ConfigurationItem> preferences = new ArrayList<ConfigurationItem>();
				String[] catHier = new String[1];
				catHier[0] = reasoner.getReasonerFamily();
				ConfigurationItem ci = new ConfigurationItem(catHier);
				ci.addNameValuePair(ci.new NameValuePair("pDerivationLogging", "Shallow")); // could also be "Deep"
				preferences.add(ci);
				reasoner.initializeReasoner(getCurrentCodeModel(), getCodeModelName(), null, preferences, getCodeModelConfigMgr().getRepoType());
			}
			
			if(requestType > 1) {
				String pquery = reasoner.prepareQuery(query);
				
				ResultSet results = reasoner.ask(pquery);
				if (results == null) {
					System.err.print("\n!!! Query returned null !!!\n");
					System.err.print(pquery);
					getCurrentCodeModel().write(System.err,"N3");
					System.err.print("\n!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!\n");
				} else {
					System.out.print(results.toString());
				}
				
				queryResults = results;
			}
			else {
			
		        boolean deductionsOnly = true; //true
				Object infModel = reasoner.getInferredModel(deductionsOnly);
				if (!(infModel instanceof Model)) {
					throw new ConfigurationException("The reasoner returned an inferred model of type '" + infModel.getClass().getCanonicalName() + "'; this is not supported by SemanticAnnotator.");
				}
				Model inferredModel = (Model) infModel;
				
		//		OntModel infOntModel = ModelFactory.createOntologyModel(getCurrentCodeModel().getSpecification(), inferredModel);
		//
		//		infOntModel.write(System.out, "N3");
				
		//		setInferredModel(infOntModel);
		
				
		//		inferredModel = inferredModel.remove(aggregateImports);
				
		//		aggregateImports.write(System.out, "N3");
				
		//		inferredModel.write(System.out, "N3");
				
				getCurrentCodeModel().add(inferredModel);
				
		//		getCurrentCodeModel().write(System.out, "N3"); //"N3" "RDF/XML"
			
			}
			
	//		getCodeModelConfigMgr().clearReasoner();

		}
		
		return true;
	}

//	private class GrFN_ExpressionNodeInstanceCreator implements InstanceCreator<GrFN_ExpressionNode> {
//		public GrFN_ExpressionNode createInstance(Type type) {
//			return new GrFN_ExpressionNode(uid);
//		}
//	}

	// aske/processing/imports/JavaModelExtractorJP.java initializeCodeModel
	private void initializeGrFNModel(String extractionModelModelFolder) throws ConfigurationException, IOException, TranslationException {
		// create new code model
		String repoType;
		setCodeModelConfigMgr(ConfigurationManagerForIdeFactory.getConfigurationManagerForIDE(extractionModelModelFolder, null)); 
		OntDocumentManager owlDocMgr = getCodeModelConfigMgr().getJenaDocumentMgr();
		OntModelSpec spec = new OntModelSpec(OntModelSpec.OWL_MEM);
		if (extractionModelModelFolder != null) { 
			File mff = new File(extractionModelModelFolder);
			mff.mkdirs();
			try {
				repoType = getCodeModelConfigMgr().getRepoType();
			} catch (Exception e) {
				repoType = "RDF/XML";
			}

			if (repoType == null) {
				repoType = "RDF/XML";
			}
			spec.setImportModelGetter(getCodeModelConfigMgr().getSadlModelGetterPutter(repoType));
//			spec.setImportModelGetter(new SadlJenaModelGetterPutter(spec, extractionModelModelFolder));
		}
		if (owlDocMgr != null) {
			spec.setDocumentManager(owlDocMgr);
			owlDocMgr.setProcessImports(true);
		}
		setCurrentCodeModel(ModelFactory.createOntologyModel(spec));	
		getCurrentCodeModel().setNsPrefix(getCodeModelPrefix(), getCodeModelNamespace()); 
		Ontology modelOntology = getCurrentCodeModel().createOntology(getCodeModelName());
		logger.debug("Ontology '" + getCodeModelName() + "' created");
		modelOntology.addComment("This ontology was created by extraction from GrFN json by the GraSEN SemanticAnnotator.", "en");
		setCodeMetaModelUri(GRFN_EXTRACTION_MODEL_URI);
		setCodeMetaModelPrefix(GRFN_EXTRACTION_MODEL_PREFIX);
		
//		OntModel grfnExtractionModel = getCodeModelConfigMgr().getOntModel(getCodeMetaModelUri(), Scope.INCLUDEIMPORTS);
//		addImportToJenaModel(getCodeModelName(), getCodeMetaModelUri(), getCodeMetaModelPrefix(), grfnExtractionModel);
//
//		aggregateImports = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM, grfnExtractionModel);
//
//		OntModel sadlImplicitModel = getCodeModelConfigMgr().getOntModel(getSadlImplicitModelUri(), Scope.INCLUDEIMPORTS);
//		addImportToJenaModel(getCodeModelName(), getSadlImplicitModelUri(), 
//				getCodeModelConfigMgr().getGlobalPrefix(getSadlImplicitModelUri()), sadlImplicitModel);
//		aggregateImports.add(sadlImplicitModel);
//		
//		String listmodelurl = getCodeModelConfigMgr().getAltUrlFromPublicUri(getSadlListModelUri());
//		if (listmodelurl != null && !listmodelurl.equals(getSadlListModelUri())) {
//			if (new File((new SadlUtils()).fileUrlToFileName(listmodelurl)).exists()) {
//				OntModel sadlListModel = getCodeModelConfigMgr().getOntModel(getSadlListModelUri(), Scope.INCLUDEIMPORTS);
//				addImportToJenaModel(getCodeModelName(), getSadlListModelUri(), 
//						getCodeModelConfigMgr().getGlobalPrefix(getSadlListModelUri()), sadlListModel);
//				aggregateImports.add(sadlListModel);
//			}
//			else {
//				System.err.println("Project is missing SadlListModel. This should not happen.");
//			}
//		}
//
		//Import the semantic annotator model 
		OntModel semAnnotatorModel = getCodeModelConfigMgr().getOntModel(SEMANNOTATOR_MODEL_URI, Scope.INCLUDEIMPORTS);
//		addImportToJenaModel(getCodeModelName(), SEMANNOTATOR_MODEL_URI, SEMANNOTATOR_MODEL_PREFIX, semAnnotatorModel);
		addImportToJenaModel(getCodeModelName(), SEMANNOTATOR_MODEL_URI, null, semAnnotatorModel);
//		aggregateImports.add(semAnnotatorModel);
		
//		OntModel sadlBuiltinsModel = getCodeModelConfigMgr().getOntModel("http://sadl.org/builtinfunctions", Scope.INCLUDEIMPORTS);
//		aggregateImports.add(sadlBuiltinsModel);
		
	}


	
	
	
	
	private void parse(String inputIdentifier, String modelFolder, String grFNjsonContent) throws IOException, ConfigurationException {
		if (logger.isDebugEnabled()) {
			logger.debug("***************** GrFN Json to process ******************");
			logger.debug(grFNjsonContent);
			logger.debug("*********************************************************");
		}

		Gson gson = new Gson();
		
		GrFN_Combined gr = null; //= new GrFN_Graph();
		GrFN_ExpressionTree[] expr;

		try {
			gr = gson.fromJson(grFNjsonContent, GrFN_Combined.class);
			if (gr.getGrfn().getUid() != null) {
				processGrFN(gr.getGrfn());     	
			} 
			else { 
				if (logger.isDebugEnabled()) {
					logger.debug(" json is not a valid combined GrFN json");
				}	
			}
		}catch (Exception e) {
//			e.printStackTrace();
		}

		try {
		expr = gr.getExpTreeArray();
		for (GrFN_ExpressionTree e : expr) {
			processGrFN_ExpressionTree(e);
		}
	} catch (Exception e) {
//		e.printStackTrace();
	}
		
//		try {
//			gr = gson.fromJson(grFNjsonContent, GrFN_Graph.class);
//			if (gr.getUid() != null) {
//				processGrFN(gr);     	
//			} 
//			else { 
//				if (logger.isDebugEnabled()) {
//					logger.debug(" json is not a GrFN Graph ");
//				}	
//			}
//		}catch (Exception e) {
////			e.printStackTrace();
//		}
//			
//		try {
//			expr = gson.fromJson(grFNjsonContent, GrFN_ExpressionTree[].class);
//			for (GrFN_ExpressionTree e : expr) {
//				processGrFN_ExpressionTree(e);
//			}
//		} catch (Exception e) {
////			e.printStackTrace();
//		}
		
		
		/*****
		// ACM may already save the owl model as sadl. See ACM line 435
		// If not, then we may need to invoke saveAsSadlFile from here

		String response = "Yes";
		Map<File, Boolean> outputOwlFiles = Map.of(owlfilename, true);
		String sadlFileName = saveAsSadlFile(Map<File, Boolean> outputOwlFiles, response)
		
		*****/
		
//        // Set up a minimal type solver that only looks at the classes used to run this sample.
//        CombinedTypeSolver combinedTypeSolver = new CombinedTypeSolver();
//        combinedTypeSolver.add(new ReflectionTypeSolver());
//
//        // Configure JavaParser to use type resolution
//        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(combinedTypeSolver);
//        JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);
//
//        // Parse some code
//        CompilationUnit cu = JavaParser.parse(javaCodeContent);
//        Optional<PackageDeclaration> pkg = cu.getPackageDeclaration();
//        if (pkg.isPresent()) {
//        	setPackageName(pkg.get().getNameAsString());
//        	String rootClassName = getRootClassName(cu);
//        	setCodeModelName(getPackageName() + "/" + rootClassName);
//        	setCodeModelPrefix(derivePrefixFromPackage(getCodeModelName()));
//        }
//        initializeCodeModel(getCurationMgr().getOwlModelsFolder());
//        findAllClassesToIgnore(cu);
//        processBlock(cu, null);
//        postProcess();
	}

//	private void processGrFN_ExpressionArray(GrFN_ExpressionArray expr) {
//		for(GrFN_ExpressionTree e : expr.getExpressions()) {
//			processGrFN_ExpressionTree(e);
//		}
//	}

//	private void processCombinedGrFN(GrFN_Combined gr) {
//		processGrFN(gr.getGrfn());
//		
//		processGrFN_ExpressionTree(gr.getExpTreeArray());
//	}

	private void processGrFN_ExpressionTree(GrFN_ExpressionTree e) {
		Individual eInst = getOrCreateExpressionTree(e);
		for(GrFN_ExpressionNode nd : e.getNodes()) {
			processExpressionNode(eInst, nd);
		}
	}

	private void processExpressionNode(Individual eInst, GrFN_ExpressionNode nd) {
		String p = nd.getType();
		Individual ndInst = null;

		//If it is a variable, the instance may already exist
		if (p.equals("VARIABLE") ) {
			String uid = nd.getUid();
			ndInst = getOrCreateVariable(uid);
			p = nd.getIdentifier();
			if (p != null) {
				//Note: If ndInst already exists, it may already have an identifier
				addDataProperty(ndInst, getVarnameProperty(), p);
			}
			p = nd.getGrfn_uid();
			if (p != null && !p.equals("")) {
				Individual vInst = getOrCreateVariable(p);
				ndInst.addProperty(getGrFNUidProperty(), vInst);
			}
		}
		else { //if not a variable, it is an OPERATOR or VALUE
			String uid = nd.getUid();
			ndInst = getOrCreateExpNode(uid);
			p = nd.getOperator();
			if (p != null) {
				addDataProperty(ndInst, getOperatorProperty(), p);
			}
			p = nd.getValue();
			if (p != null) {
				addDataProperty(ndInst, getNodeValueProperty(), p);	
			}
		}
		
		eInst.addProperty(getNodesProperty(), ndInst);
		p = nd.getType();
		addDataProperty(ndInst, getNodeTypeProperty(), p);
		
		// Only operators have children
		List<String> children = nd.getChildren();
		List<Individual> childInstances = new ArrayList<Individual>();
		if(children != null) {
			for (String c : children) { // children are expression tree node uids
				//child may be a variable or an operator. In either case, it is an Node
				Individual cInst = getOrCreateExpNode(c);
				childInstances.add(cInst);
			}
			if (childInstances.size() > 0) {
				try {
					Individual typedList = addMembersToList(getCurrentCodeModel(), null, getExpNodeListClass(), getExpNodeClass(), childInstances.iterator());
					ndInst.addProperty(getChildrenProperty(), typedList);
				} catch (JenaProcessorException e) {
					e.printStackTrace();
				} catch (TranslationException e) {
					e.printStackTrace();
				}
			}
		}
		
//		processMetadata(nd, ndInst);
		 
	}

	private void processMetadata(GrFN_Node nd, Individual ndInst) {
		List<GrFN_Metadata> md = nd.getMetadata();
		Individual mdInst = createNewMetadata();
		ndInst.addProperty(getMetadataProperty(), mdInst);
		int bl,el;
		if (md != null) {
			for (GrFN_Metadata mdEntry : md) {
				if (mdEntry.getType().equals("code_span_reference") && mdEntry.getCode_span() != null) {
					bl = mdEntry.getCode_span().getLine_begin();
					if (bl > 0) {
						mdInst.addLiteral(getLineBeginsProperty(), mdEntry.getCode_span().getLine_begin());						
					}
					el = mdEntry.getCode_span().getLine_end();
					if (el > 0) {
						mdInst.addLiteral(getLineEndsProperty(), mdEntry.getCode_span().getLine_end());						
					}
				}
				else if (mdEntry.getType().toLowerCase().equals("from_source")) {
					boolean fs = mdEntry.getFrom_source();
					mdInst.addLiteral(getFromSourceProperty(), fs);
				}
			}
		}
	}

	private void processGrFN(GrFN_Graph gr) {

		Individual grfnInst = getOrCreateGraph(gr);

		String p = gr.getDescription();
		if (p != null) {
			addDataProperty(grfnInst, getDescriptionProperty(), p);
		}

		p = gr.getIdentifier();
		if (p != null) {
			addDataProperty(grfnInst, getIdentifierProperty(), p);
		}

		p = gr.getDate_created();
		if (p != null && !p.equals("timestamp")) {
			addDataProperty(grfnInst, getDateCreatedProperty(), p);
		}

		List<GrFN_Function> fns = gr.getFunctions();
		for (GrFN_Function fn : fns) {
			processFunction(grfnInst, fn);
		}

		List<GrFN_Variable> vars = gr.getVariables();
		for (GrFN_Variable var : vars) {
			processVariable(grfnInst, var);
		}

		List<GrFN_Hyperedge> edges = gr.getHyperedges();
		for (GrFN_Hyperedge edge : edges) {
			processEdge(grfnInst, edge);
		}

		List<GrFN_Subgraph> subgraphs = gr.getSubgraphs();
		for (GrFN_Subgraph sg : subgraphs) {
			processSubgraph(grfnInst, sg);
		}
		
//		List<GrFN_Object> objects = gr.getObjects();
//		for (GrFN_Object obj : objects) {
//			processObjects(grfnInst, obj);
//		}

		List<GrFN_Type> types = gr.getTypes();
		for (GrFN_Type ty : types) {
			processTypes(grfnInst, ty);
		}

	}

	private void processTypes(Individual grfnInst, GrFN_Type type) {
		Individual typeInst = getOrCreateType(type);
//		grfnInst.addProperty(getObjectsProperty(), typeInst);
		String p = type.getUid();
		if (p != null) {
			addDataProperty(typeInst, getUidProperty(), p);
		}
		p = type.getName();
		if (p != null) {
			addDataProperty(typeInst, getNameProperty(), p);
		}
		List<Field> fields = type.getFields();
		for (Field f : fields) {
			Individual fieldInst = createNewField();
			typeInst.addProperty(getFieldsProperty(), fieldInst);
			p = f.getName();
			if (p != null) {
				addDataProperty(fieldInst, getNameProperty(), p);
			}
			p = f.getType();
			if (p != null) {
				addDataProperty(fieldInst, getDataTypeProperty(), p);
			}
		}
		
		processMetadata((GrFN_Node)type, typeInst);

	}


//	private void processObjects(Individual grfnInst, GrFN_Object obj) {
//		Individual objInst = getOrCreateObject(obj);
//		grfnInst.addProperty(getObjectsProperty(), objInst);
//		String p = obj.getUid();
//		if (p != null) {
//			addDataProperty(objInst, getUidProperty(), p);
//		}
//		String type = obj.getType();
//		Individual typeInst = getOrCreateType(type);
//		objInst.addProperty(getObjTypeProperty(), typeInst);
//		
//		
////		processMetadata((GrFN_Node)obj, objInst);
//
//	}



	private void processSubgraph(Individual grfnInst, GrFN_Subgraph sg) {
		Individual sgInst = getOrCreateSubgraph(sg); 
		grfnInst.addProperty(getSubgraphsProperty(), sgInst);
		String p = sg.getUid();
		if (p != null) {
			addDataProperty(sgInst, getUidProperty(), p);
		}
		p = sg.getName();
		if (p != null) {
			addDataProperty(sgInst, getNameProperty(), p);
		}
		p = sg.getType();
		if (p != null) {
			addDataProperty(sgInst, getSGTypeProperty(), p);
		}
		p = sg.getScope();
		if (p != null) {
			addDataProperty(sgInst, getScopeProperty(), p);
		}
		p = sg.getNamespace();
		if (p != null) {
			addDataProperty(sgInst, getNamespaceProperty(), p);
		}
		List<String> nodes = sg.getNodes();
		for (String n : nodes) {
			Individual nInst = getNodeInst(n);
			sgInst.addProperty(getSubgraphNodesProperty(), nInst);
		}
		p = sg.getParent();
		if (p != null) { // a subgraph may not have a parent
			Individual parInst = getOrCreateSubgraph(p);
			if (parInst != null) {
				sgInst.addProperty(getParentProperty(), parInst);
			}
		}
		
		processMetadata((GrFN_Node)sg, sgInst);

	}

	private void processEdge(Individual grfnInst, GrFN_Hyperedge edge) {
		Individual edgeInst = createNewEdge();
		grfnInst.addProperty(getEdgesProperty(), edgeInst);
		String fuid = edge.getFunction();
		List<String> inputs = edge.getInputs();
		List<String> outputs = edge.getOutputs();
		Individual fnInst = getOrCreateFunction(fuid);
		edgeInst.addProperty(getEdgeFunctionProperty(), fnInst);
		
		List<Individual> inputInstances = new ArrayList<Individual>();
		if(inputs != null) {
			for (String in : inputs) {
				//child may be a variable or an operator. In either case, it is an Node
				Individual inInst = getOrCreateVariable(in);
				inputInstances.add(inInst);
				edgeInst.addProperty(getEdgeInputProperty(), inInst);
			}
			if (inputInstances.size() > 0) {
				try {
					Individual typedList = addMembersToList(getCurrentCodeModel(), null, getEdgeInputsListClass(), getVariableClass(), inputInstances.iterator());
					edgeInst.addProperty(getEdgeInputListProperty(), typedList);
				} catch (JenaProcessorException e) {
					e.printStackTrace();
				} catch (TranslationException e) {
					e.printStackTrace();
				}
			}
		}

		List<Individual> outputInstances = new ArrayList<Individual>();
		if(outputs != null) {
			for (String out : outputs) {
				//child may be a variable or an operator. In either case, it is an Node
				Individual outInst = getOrCreateVariable(out);
				outputInstances.add(outInst);
				edgeInst.addProperty(getEdgeOutputProperty(), outInst);
			}
			if (outputInstances.size() > 0) {
				try {
					Individual typedList = addMembersToList(getCurrentCodeModel(), null, getEdgeOutputsListClass(), getVariableClass(), outputInstances.iterator());
					edgeInst.addProperty(getEdgeOutputListProperty(), typedList);
				} catch (JenaProcessorException e) {
					e.printStackTrace();
				} catch (TranslationException e) {
					e.printStackTrace();
				}
			}
		}

//		for (String inp : inputs) {
//			Individual inpInst = getOrCreateVariable(inp);
//			edgeInst.addProperty(getEdgeInputProperty(), inpInst);
//		}
//		for (String outp : outputs) {
//			Individual outpInst = getOrCreateVariable(outp);
//			edgeInst.addProperty(getEdgeOutputProperty(), outpInst);
//		}
	}


	private void processVariable(Individual grfnInst, GrFN_Variable var) {
		Individual varInst = getOrCreateVariable(var);
		String p = var.getIdentifier();
		if (p != null) {
			addDataProperty(varInst, getIdentifierProperty(), p);
		}
		p = var.getUid();
		if (p != null) {
			addDataProperty(varInst, getUidProperty(), p);
		}
		p = var.getData_type();
		if (p != null) {
			addDataProperty(varInst, getDataTypeProperty(), p);
		}
		
		processMetadata((GrFN_Node)var, varInst);

	}




	private void processFunction(Individual grfnInst, GrFN_Function fn) {
		Individual fnInst = getOrCreateFunction(fn);
//		addObjectProperty(grfnInst,getFunctionProperty(), fnInst);
		String p = fn.getLambda();
		if (p != null) {
			addDataProperty(fnInst, getLambdaProperty(), p);
		}
		p = fn.getType();
		if (p != null) {
			addDataProperty(fnInst, getFunctionTypeProperty(), p);
		}
		
		processMetadata((GrFN_Node)fn, fnInst);

	}


//	private void addObjectProperty(Individual subject, Property property, Individual object) {
//		subject.addProperty(property, object);		
//	}

	private void addDataProperty(Individual subject, Property property, String d) {
		subject.addProperty(property, getCurrentCodeModel().createTypedLiteral(d));
	}

	private Individual getOrCreateGraph(GrFN_Graph gr) {
		Individual graphInst = null;
		String uid = gr.getUid();
		graphInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
    	if (graphInst == null) {
    		graphInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getGrFNClass());
    	}
    	else {
    		System.out.println("Something's wrong. This GrFN already exists!");
    	}
		return graphInst;
	}
	
	private Individual getOrCreateFunction(GrFN_Function fn) {
		Individual fnInst = null;
		String uid = fn.getUid();
		fnInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
    	if (fnInst == null) {
    		fnInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getFunctionClass());
    	}
    	else {
//    		System.out.println("Something's wrong. This Function already exists!");
    	}
		return fnInst;
	}


	private Individual getOrCreateFunction(String uid) {
		Individual fnInst = null;
		if (uid.length() > 0 && Character.isDigit(uid.toCharArray()[0]) ) {
			uid = "_" + uid;
		}
		uid = uid.replaceAll(":", "_");
		fnInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
    	if (fnInst == null) {
    		fnInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getFunctionClass());
    	}
    	else {
//    		System.out.println("Something's wrong. This Function already exists!");
    	}
		return fnInst;
	}

	
	
	private Individual getOrCreateVariable(GrFN_Variable var) {
		Individual varInst = null;
		String uid = var.getUid();
		uid = uid.replaceAll(":", "_");
		varInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
		if (varInst == null) {
			varInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getVariableClass());
		}
    	else {
//    		System.out.println("Something's wrong. Variable " + uid + " already exists!");
    	}
		return varInst;
	}
	
	
	private Individual getOrCreateVariable(String uid) {
		Individual varInst = null;
		if (uid.length() > 0 && Character.isDigit(uid.toCharArray()[0]) ) {
			uid = "_" + uid;
		}
		uid = uid.replaceAll(":", "_");
		varInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
		if (varInst == null) {
			varInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getVariableClass());
		}
    	else {
//    		System.out.println("Something's wrong. Variable " + inp + " already exists!");
    	}
		return varInst;
	}

	//TODO
	private Individual getOrCreateExpressionTree(GrFN_ExpressionTree e) {
		Individual eInst = null;
		String uid = e.getUid();
		eInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
    	if (uid == null || eInst == null) {
//    		eInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getExpTreeClass());
    		//Modify: no longer have ExpressionTree class.
    		eInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getFunctionClass());

    	}
    	//If eInst already exists (it's a Function in the GrFN), make it an instance of ExpressionTree
//    	else if (eInst != null) {
//    		eInst.addRDFType(getExpTreeClass()); //Modify: no longer have ExpressionTree class.
//    	}
		return eInst;
	}


	
	private Individual createNewEdge() {
		Individual edgeInst = getCurrentCodeModel().createIndividual(getEdgeClass());
		return edgeInst;
	}


	private Individual createNewMetadata() {
		Individual mdInst = getCurrentCodeModel().createIndividual(getMetadataClass());
		return mdInst;
	}

	private Individual createNewField() {
		Individual fieldInst = getCurrentCodeModel().createIndividual(getFieldClass());
		return fieldInst;
	}
	
	private Individual getOrCreateSubgraph(GrFN_Subgraph sg) {
		Individual sgInst = null;
		String uid = sg.getUid();
		sgInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
		if (sgInst == null) {
			sgInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getSubgraphClass());
		}
		return sgInst;
	}
	private Individual getOrCreateSubgraph(String uid) {
		Individual sgInst = null;
		if (uid.length() > 0 && Character.isDigit(uid.toCharArray()[0]) ) {
			uid = "_" + uid;
		}
		uid = uid.replaceAll(":", "_");
		sgInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
		if (sgInst == null) {
			sgInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getSubgraphClass());
		}
		return sgInst;
	}
	
//	private Individual getOrCreateObject(GrFN_Object obj) {
//		Individual objInst = null;
//		String uid = obj.getUid();
//		objInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
//		if (objInst == null) {
//			objInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getObjectClass());
//		}
//		return objInst;
//
//	}
	
	private Individual getOrCreateType(GrFN_Type type) {
		Individual typeInst = null;
		String tuid = type.getName();  //type.getUid(); UofA removed uids for types
		typeInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + tuid);
		if (typeInst == null) {
			typeInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + tuid, getTypeClass());
		}
		return typeInst;
	}

	
//	private Individual getOrCreateType(String uid) {
//		Individual typeInst = null;
//		typeInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
//		if (typeInst == null) {
//			typeInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getTypeClass());
//		}
//		return typeInst;
//	}
	
//	private Individual createNewSubgraph() {
//		Individual sgInst = getCurrentCodeModel().createIndividual(getSubgraphClass());
//		return sgInst;
//	}
	
//	private Individual getOrCreateNode(GrFN_Node node) {
//		Individual ndInst = null;
//		String uid = node.getUid();
//		ndInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
//		if (ndInst == null) {
//			ndInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getNodeClass());
//		}
//		return ndInst;
//	}

//	private Individual getOrCreateNode(String uid) {
//		Individual ndInst = null;
//		if (uid.length() > 0 && Character.isDigit(uid.toCharArray()[0]) ) {
//			uid = "_" + uid;
//		}
//		ndInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
//		if (ndInst == null) {
//			ndInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getNodeClass());
//		}
//		return ndInst;
//	}

	private Individual getNodeInst(String uid) {
		Individual ndInst = null;
		if (uid.length() > 0 && Character.isDigit(uid.toCharArray()[0]) ) {
			uid = "_" + uid;
		}
		uid = uid.replaceAll(":", "_");
		ndInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
		if (ndInst == null) {
			System.out.println("Something's wrong. Subgraph node with " + uid + " does not exist!");
		}
		return ndInst;
	}


//	private Individual getOrCreateExpNode(GrFN_ExpressionNode nd) {
//		Individual ndInst = null;
//		String uid = nd.getUid();
//		ndInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
//		if (ndInst == null) {
//			ndInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getExpNodeClass());
//		}
//		return ndInst;
//	}
	private Individual getOrCreateExpNode(String uid) {
		Individual ndInst = null;
		if (uid.length() > 0 && Character.isDigit(uid.toCharArray()[0]) ) {
			uid = "_" + uid;
		}
		uid = uid.replaceAll(":", "_");
		ndInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
		if (ndInst == null) {
			ndInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getExpNodeClass());
		}
		return ndInst;
	}

//	private Individual getOrCreateNodeAndVar(String uid) {
//		Individual ndInst = null;
//		if (uid.length() > 0 && Character.isDigit(uid.toCharArray()[0]) ) {
//			uid = "_" + uid;
//		}
//		ndInst = getCurrentCodeModel().getIndividual(getCodeModelNamespace() + uid);
//		if (ndInst == null) {
//			ndInst = getCurrentCodeModel().createIndividual(getCodeModelNamespace() + uid, getNodeAndVarClass());
//		}
//		return ndInst;
//	}


	
	/**
	 * Method to add an import to the model identified by modelName
	 * @param modelName
	 * @param importUri
	 * @param importPrefix
	 * @param importedOntModel
	 */
	private void addImportToJenaModel(String modelName, String importUri, String importPrefix, Model importedOntModel) {
		getCurrentCodeModel().getDocumentManager().addModel(importUri, importedOntModel, true);
		Ontology modelOntology = getCurrentCodeModel().createOntology(modelName);
		if (importPrefix != null) {
			getCurrentCodeModel().setNsPrefix(importPrefix, ensureHashOnUri(importUri));
		}
		org.apache.jena.rdf.model.Resource importedOntology = getCurrentCodeModel().createResource(importUri);
		modelOntology.addImport(importedOntology);
		getCurrentCodeModel().addSubModel(importedOntModel);
		getCurrentCodeModel().addLoadedImport(importUri);
	}
	private String ensureHashOnUri(String uri) {
		if (!uri.endsWith("#")) {
			uri+= "#";
		}
		return uri;
	}

	private String getSadlImplicitModelUri() {
		return "http://sadl.org/sadlimplicitmodel";
	}

	private String getSadlListModelUri() {
		return "http://sadl.org/sadllistmodel";
	}

//	private String getSemAnnotatorModelUri() {
//		return GRFN_EXTRACTION_MODEL_URI; //"http://sadl.org/SemAnnotator.sadl";
//	}
	
	public String getCodeMetaModelUri() {
		return grFNExtractionModelUri;
	}

	public void setCodeMetaModelUri(String codeMetaModelUri) {
		this.grFNExtractionModelUri = codeMetaModelUri;
	}
	
	public String getCodeMetaModelPrefix() {
		return grFNExtractionModelPrefix;
	}

	public void setCodeMetaModelPrefix(String codeMetaModelPrefix) {
		this.grFNExtractionModelPrefix = codeMetaModelPrefix;
	}
	

	private Resource getGrFNClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#GrFN");
	}
//	private Resource getNodeClass() {
//		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#Node");
//	}
	private Resource getFunctionClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#Function");
	}
	private Resource getVariableClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#Variable");
	}
	private Resource getEdgeClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#HyperEdge");
	}
	private Resource getSubgraphClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#SubGraph");
	}
//	private Resource getObjectClass() {
//		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#Object");
//	}
	private Resource getTypeClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#Type");
	}
	private Resource getFieldClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#Field");
	}
	private Resource getExpTreeClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#ExpressionTree");
	}
	private Resource getExpNodeClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#ExpNode");
	}
//	private Resource getNodeAndVarClass() {
//		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#NodeAndVariable");
//	}
	private Resource getMetadataClass() {
		return getCurrentCodeModel().getOntClass(getCodeMetaModelUri() + "#Metadata");
	}
	private Property getDescriptionProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#description");
	}
	private Property getUidProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#uid");
	}
	private Property getIdentifierProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#identifier");
	}
	private Property getDateCreatedProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#date_created");
	}
//	private Property getFunctionProperty() {
//		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#functions");
//	}
	private Property getLambdaProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#lambda");
	}
	private Property getFunctionTypeProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#ftype");
	}
	private Property getNameProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#name");
	}
	private Property getDataTypeProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#data_type");
	}
	private Property getEdgeInputProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#inputs");
	}
	private Property getEdgeInputListProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#inputList");
	}
	private OntClass getEdgeInputsListClass() {
		Property inputsProp = getEdgeInputListProperty();
		StmtIterator stmtItr = getCurrentCodeModel().listStatements(inputsProp, RDFS.range, (RDFNode)null);
		if (stmtItr.hasNext()) {
			RDFNode rng = stmtItr.nextStatement().getObject();
			if (rng.asResource().canAs(OntClass.class)) {
				return rng.asResource().as(OntClass.class);
			}
		}
		return null;
	}
	
	private Property getEdgeOutputProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#outputs");
	}
	private Property getEdgeOutputListProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#outputList");
	}
	private OntClass getEdgeOutputsListClass() {
		Property outputsProp = getEdgeOutputListProperty();
		StmtIterator stmtItr = getCurrentCodeModel().listStatements(outputsProp, RDFS.range, (RDFNode)null);
		if (stmtItr.hasNext()) {
			RDFNode rng = stmtItr.nextStatement().getObject();
			if (rng.asResource().canAs(OntClass.class)) {
				return rng.asResource().as(OntClass.class);
			}
		}
		return null;
	}

	private Property getEdgesProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#hyper_edges");
	}
	private Property getEdgeFunctionProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#function");
	}
	private Property getSubgraphsProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#subgraphs");
	}
	private Property getSGTypeProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#sg_type");
	}
	private Property getScopeProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#scope");
	}
	private Property getNamespaceProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#namespace");
	}
//	private Property getObjectsProperty() {
//		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#objects");
//	}
//	private Property getObjTypeProperty() {
//		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#obj_type");
//	}
	private Property getSubgraphNodesProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#nodes");
	}
	private Property getParentProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#parent");
	}
	private Property getFieldsProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#fields");
	}
	private Property getNodeTypeProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#exp_node_type");
	}
	private Property getVarnameProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#var_name");
	}
	private Property getOperatorProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#operator");
	}
	private Property getChildrenProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#children");
	}
	private Property getNodesProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#nodes");
	}
	private Property getNodeValueProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#node_value");
	}
	private Property getGrFNUidProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#grfn_uid");
	}
	private Property getMetadataProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#metadata");
	}
	private Property getLineBeginsProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#line_begin");
	}
	private Property getLineEndsProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#line_end");
	}
	private Property getFromSourceProperty() {
		return getCurrentCodeModel().getOntProperty(getCodeMetaModelUri() + "#from_source");
	}


	private OntClass getExpNodeListClass() {
		Property childrenProp = getChildrenProperty();
		StmtIterator stmtItr = getCurrentCodeModel().listStatements(childrenProp, RDFS.range, (RDFNode)null);
		if (stmtItr.hasNext()) {
			RDFNode rng = stmtItr.nextStatement().getObject();
			if (rng.asResource().canAs(OntClass.class)) {
				return rng.asResource().as(OntClass.class);
			}
		}
		return null;
	}

//	private OntClass getLinesListClass() {
//		Property linesProp = getLinesProperty();
//		StmtIterator stmtItr = getCurrentCodeModel().listStatements(linesProp, RDFS.range, (RDFNode)null);
//		if (stmtItr.hasNext()) {
//			RDFNode rng = stmtItr.nextStatement().getObject();
//			if (rng.asResource().canAs(OntClass.class)) {
//				return rng.asResource().as(OntClass.class);
//			}
//		}
//		return null;
//	}

	

	/**
	 * Method to convert an Iterator over a List of values to a SADL Typed List in the provided model
	 * @param lastInst -- the list to which to add members
	 * @param cls -- the class of the SADL list
	 * @param type --  the type of the members of the list
	 * @param memberIterator -- Iterator over the values to add
	 * @return -- the list instance
	 * @throws JenaProcessorException
	 * @throws TranslationException
	 */
	protected Individual addMembersToList(OntModel model, Individual lastInst, OntClass cls,
			org.apache.jena.rdf.model.Resource type, Iterator<?> memberIterator) throws JenaProcessorException, TranslationException {
		if (lastInst == null) {
			lastInst = model.createIndividual(cls);
		}
		Object val = memberIterator.next();
		if (val instanceof Individual) {
			Individual listInst = (Individual) val;
			if (type.canAs(OntClass.class)) {
				ExtendedIterator<org.apache.jena.rdf.model.Resource> itr = listInst.listRDFTypes(false);
				boolean match = false;
				while (itr.hasNext()) {
					org.apache.jena.rdf.model.Resource typ = itr.next();
					if (typ.equals(type)) {
						match = true;
					} else {
						try {
							if (typ.canAs(OntClass.class) && SadlUtils.classIsSubclassOf(typ.as(OntClass.class), type.as(OntClass.class), true, null)) {
								match = true;
							}
						} catch (CircularDependencyException e) {
							e.printStackTrace();
						}
					}
					if (match) {
						break;
					}
				}
				if (!match) {
					throw new JenaProcessorException("The Instance '" + listInst.toString() + "' doesn't match the List type.");
				}
				model.add(lastInst, model.getProperty(SadlConstants.SADL_LIST_MODEL_FIRST_URI),
						listInst);
			} else {
				throw new JenaProcessorException("The type of the list could not be converted to a class.");
			}
		} else {
			Literal lval;
			if (val instanceof Literal) {
				lval = (Literal) val;
			}
			else {
				lval = SadlUtils.getLiteralMatchingDataPropertyRange(model,type.getURI(), val);
			}
			model.add(lastInst, model.getProperty(SadlConstants.SADL_LIST_MODEL_FIRST_URI), lval);
		}
		if (memberIterator.hasNext()) {
			Individual rest = addMembersToList(model, null, cls, type, memberIterator);
			model.add(lastInst, model.getProperty(SadlConstants.SADL_LIST_MODEL_REST_URI), rest);
		}
		return lastInst;
	}

	
	
	public File saveAsOwlFile(String outputFilename) throws ConfigurationException, IOException {
		File of = new File(new File(getOwlModelsFolder())
				//.getParent() + "/" + EXTRACTED_MODELS_FOLDER_PATH_FRAGMENT 
				+ File.separator + outputFilename);
		//of.getParentFile().mkdirs();
		getCodeModelConfigMgr().saveOwlFile(getCurrentCodeModel(), getCodeModelName(), of.getCanonicalPath());

		String outputOwlFileName = of.getCanonicalPath();			
		addCodeModel(outputOwlFileName, getCurrentCodeModel());

		String altUrl;
		try {
			altUrl = (new SadlUtils()).fileNameToFileUrl(outputOwlFileName);
			getCodeModelConfigMgr().addMapping(altUrl, getCodeModelName(), getCodeModelPrefix(), false, "GrFNModelExtractor");
			getCodeModelConfigMgr().addJenaMapping(getCodeModelName(), altUrl);
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		return of;
	}

	
	/**
	 * Method to save the OWL model created from GrFN json extraction as a SADL file
	 * @param outputOwlFiles -- List of names of the OWL file created
	 * @return -- the sadl fully qualified file name
	 * @throws IOException
	 */
	public List<String> saveAsSadlFile(Map<File, Integer> outputOwlFiles, String response) throws IOException {
		List<String> sadlFileNames = new ArrayList<String>();
		for (File outputOwlFile : outputOwlFiles.keySet()) {
			int isCodeExtract = outputOwlFiles.get(outputOwlFile);
			String key = outputOwlFile.getCanonicalPath();
			OntModel mdl = null;
			IConfigurationManagerForIDE cfgmgr = null;
			String mdlName = null;
			if (isCodeExtract == 1) {
				mdl = getCodeModel(key);
				cfgmgr = getCodeModelConfigMgr();
				mdlName = getCodeModelName();
			}
			else if (isCodeExtract == 2) {
				mdl = getCodeModel(key);
				cfgmgr = getCodeModelConfigMgr();
				mdlName = getCodeModelName();
			}
			if (mdl != null) {
				OwlToSadl ots = new OwlToSadl(mdl, mdlName);
				String sadlFN = outputOwlFile.getCanonicalPath() + ".sadl";
				File sf = new File(sadlFN);
				if (sf.exists()) {
					sf.delete();
				}
				try {
					boolean status = ots.saveSadlModel(sadlFN);
					if (status) {
						sadlFileNames.add(sadlFN);
						/* We want to move the OWL file to the OwlModels folder so that it will exist,
						 * and change the mappings. If we aren't in the SADL IDE (e.g., JUnit tests),
						 * the .sadl file will not get rebuilt. If we are, it should be replaced(?).
						 */
						String newOwlFileName = cfgmgr.getModelFolder() + "/" + SadlUtils.replaceFileExtension(sf.getName(), "sadl", "owl");
						File newOwlFile = new File(newOwlFileName);
						if (newOwlFile.exists()) {
							newOwlFile.delete();
						}
						if (outputOwlFile.renameTo(newOwlFile)) {
							String altUrl;
							try {
								String prefix = cfgmgr.getGlobalPrefix(mdlName);
								altUrl = (new SadlUtils()).fileNameToFileUrl(newOwlFile.getCanonicalPath());
								cfgmgr.deleteMapping(altUrl,mdlName);
								cfgmgr.addMapping(altUrl, mdlName, prefix, true, "GrFNModelExtractor");
								cfgmgr.addJenaMapping(mdlName, altUrl);		// this will replace old mapping?
							} catch (URISyntaxException e) {
								e.printStackTrace();
							} catch (ConfigurationException e) {
								e.printStackTrace();
							}
						}
					}
				} catch (OwlImportException e) {
					e.printStackTrace();
				} catch (InvalidNameException e1) {
					e1.printStackTrace();
				}
			}
		}
		return sadlFileNames;
	}

	public IConfigurationManagerForIDE getCodeModelConfigMgr() {
		return grFNExtractionModelConfigMgr;
	}

	public void setCodeModelConfigMgr(IConfigurationManagerForIDE grFNExtractionModelConfigMgr) {
		this.grFNExtractionModelConfigMgr = grFNExtractionModelConfigMgr;
	}

	public String getOwlModelsFolder() {
		return owlModelsFolder;
	}

	public void setOwlModelsFolder(String owlModelsFolder) {
		this.owlModelsFolder = owlModelsFolder;
	}
	
	public String getCodeModelName() {
		return codeModelName;
	}

	public void setCodeModelName(String codeModelName) {
		if (codeModelName != null && !codeModelName.startsWith("http://")) {
			codeModelName = "http://" + codeModelName;
		}
		this.codeModelName = codeModelName;
	}

	public String getCodeModelPrefix() {
		return codeModelPrefix;
	}

	public void setCodeModelPrefix(String prefix) {
		this.codeModelPrefix = prefix;
	}

	public String getCodeModelNamespace() {
		return codeModelName + "#";
	}

	public void addCodeModel(String key, OntModel codeModel) {
		if (codeModels == null) {
			codeModels = new HashMap<String, OntModel>();
		}
		codeModels.put(key, codeModel);
	}

	public Map<String, OntModel> getCodeModels() {
		return codeModels;
	}

	public OntModel getCodeModel(String key) {
		if (codeModels != null) {
			return codeModels.get(key);
		}
		return null;
	}

	public void setCurrentCodeModel(OntModel m) {
		this.codeModel = m;
	}

	public OntModel getCurrentCodeModel() {
		return codeModel;
	}

	public OntModel getInferredModel() {
		return inferredModel;
	}

	public void setInferredModel(OntModel inferredModel) {
		this.inferredModel = inferredModel;
	}
}

