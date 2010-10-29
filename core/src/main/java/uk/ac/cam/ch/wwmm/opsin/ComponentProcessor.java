package uk.ac.cam.ch.wwmm.opsin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.ac.cam.ch.wwmm.opsin.ParseWord.WordType;
import uk.ac.cam.ch.wwmm.opsin.WordRules.WordRule;
import static uk.ac.cam.ch.wwmm.opsin.XmlDeclarations.*;


import nu.xom.Attribute;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import nu.xom.Node;

/**Performs structure-aware destructive procedural parsing on parser results.
*
* @author dl387
*
*/

class ComponentProcessor {
	private final static Pattern matchColon =Pattern.compile(":");
	private final static Pattern matchSemiColon =Pattern.compile(";");
	private final static Pattern matchComma =Pattern.compile(",");
	private final static Pattern matchSpace =Pattern.compile(" ");
	private final static Pattern matchElementSymbolOrAminoAcidLocant = Pattern.compile("[A-Z][a-z]?'*(\\d+[a-z]?'*)?");
	private final static Pattern matchElementSymbol = Pattern.compile("[A-Z][a-z]?");
	private final static Pattern matchNumericLocant =Pattern.compile("\\d+[a-z]?'*");
	private final static Pattern matchChalcogenReplacement= Pattern.compile("thio|seleno|telluro");
	private final static Pattern matchInlineSuffixesThatAreAlsoGroups = Pattern.compile("carbon|oxy|sulfen|sulfin|sulfon|selenen|selenin|selenon|telluren|tellurin|telluron");

	/*Holds the rules on how suffixes are interpreted. Convenience methods are available to use them*/
	private HashMap<String, HashMap<String, List<Element>>> suffixApplicability;
	private HashMap<String, Element> suffixRules;
	
	//rings that look like HW rings but have other meanings. For the HW like inorganics the true meaning is given
	private static final HashMap<String, String[]> specialHWRings = new HashMap<String, String[]>();
	static{
		//The first entry of the array is a special instruction e.g. blocked or saturated. The correct order of the heteroatoms follows
		//terminal e is ignored from all of the keys as it is optional in the input name
		specialHWRings.put("oxin", new String[]{"blocked"});
		specialHWRings.put("azin", new String[]{"blocked"});

		specialHWRings.put("oxazol", new String[]{"","O","C","N","C","C"});
		specialHWRings.put("thiazol", new String[]{"","S","C","N","C","C"});
		specialHWRings.put("selenazol", new String[]{"","Se","C","N","C","C"});
		specialHWRings.put("tellurazol", new String[]{"","Te","C","N","C","C"});
		specialHWRings.put("oxazolidin", new String[]{"","O","C","N","C","C"});
		specialHWRings.put("thiazolidin", new String[]{"","S","C","N","C","C"});
		specialHWRings.put("selenazolidin", new String[]{"","Se","C","N","C","C"});
		specialHWRings.put("tellurazolidin", new String[]{"","Te","C","N","C","C"});
		specialHWRings.put("oxazolin", new String[]{"","O","C","N","C","C"});
		specialHWRings.put("thiazolin", new String[]{"","S","C","N","C","C"});
		specialHWRings.put("selenazolin", new String[]{"","Se","C","N","C","C"});
		specialHWRings.put("tellurazolin", new String[]{"","Te","C","N","C","C"});

		specialHWRings.put("oxoxolan", new String[]{"","O","C","O","C","C"});
		specialHWRings.put("oxoxan", new String[]{"","O","C","C","O","C","C"});
		specialHWRings.put("oxoxin", new String[]{"","O","C","C","O","C","C"});

		specialHWRings.put("boroxin", new String[]{"saturated","O","B","O","B","O","B"});
		specialHWRings.put("borazin", new String[]{"saturated","N","B","N","B","N","B"});
		specialHWRings.put("borthiin", new String[]{"saturated","S","B","S","B","S","B"});
	}

	ComponentProcessor(ResourceGetter resourceGetter) throws Exception {
		//Populate suffix rules/applicability hashes
		Document suffixApplicabilityDoc = resourceGetter.getXMLDocument("suffixApplicability.xml");
		Document suffixRulesDoc = resourceGetter.getXMLDocument("suffixRules.xml");
		suffixApplicability = new HashMap<String, HashMap<String,List<Element>>>();
		suffixRules = new HashMap<String, Element>();
		Elements groupTypes = suffixApplicabilityDoc.getRootElement().getChildElements(SUFFIXAPPLICABILITY_GROUPTYPE_EL);
		for (int i = 0; i < groupTypes.size(); i++) {
			Element groupType =groupTypes.get(i);
			Elements suffixes = groupType.getChildElements(SUFFIXAPPLICABILITY_SUFFIX_EL);
			HashMap<String, List<Element>> suffixToRuleMap= new HashMap<String, List<Element>>();
			for (int j = 0; j < suffixes.size(); j++) {
				Element suffix =suffixes.get(j);
				String suffixValue= suffix.getAttributeValue(SUFFIXAPPLICABILITY_VALUE_ATR);
				if (suffixToRuleMap.get(suffixValue)!=null){//can have multiple entries if subType attribute is set
					suffixToRuleMap.get(suffixValue).add(suffix);
				}
				else{
					ArrayList<Element> suffixList =new ArrayList<Element>();
					suffixList.add(suffix);
					suffixToRuleMap.put(suffixValue, suffixList);
				}
			}
			suffixApplicability.put(groupType.getAttributeValue(SUFFIXAPPLICABILITY_TYPE_ATR), suffixToRuleMap);
		}

		Elements rules = suffixRulesDoc.getRootElement().getChildElements(SUFFIXRULES_RULE_EL);
		for (int i = 0; i < rules.size(); i++) {
			Element rule =rules.get(i);
			String ruleValue=rule.getAttributeValue(SUFFIXRULES_VALUE_ATR);
			if (suffixRules.get(ruleValue)!=null){
				throw new Exception("Suffix: " +ruleValue +" appears multiple times in suffixRules.xml");
			}
			suffixRules.put(ruleValue, rule);
		}
	}


	/** The master method, processes a parse result that has already gone through the ComponentGenerator.
	 * At this stage one can except all substituents/roots to have at least 1 group.
	 * Multiple groups are present in, for example, fusion nomenclature. By the end of this function there will be exactly 1 group
	 * associated with each substituent/root. Multiplicative nomenclature can result in there being multiple roots
	 *
	 * @param state
     * @param elem The element to process.
	 * @return
	 * @throws ComponentGenerationException 
	 * @throws StructureBuildingException 
	 */
	void process(BuildState state, Element elem) throws ComponentGenerationException, StructureBuildingException {
		List<Element> words =XOMTools.getDescendantElementsWithTagName(elem, WORD_EL);
		int wordCount =words.size();
		for (int i = wordCount -1; i>=0; i--) {
			Element word =words.get(i);
			String wordRule = OpsinTools.getParentWordRule(word).getAttributeValue(WORDRULE_EL);
			state.currentWordRule = WordRule.valueOf(wordRule);
			if (word.getAttributeValue(TYPE_ATR).equals(WordType.functionalTerm.toString())){
				continue;//functionalTerms are handled on a case by case basis by wordRules
			}

			List<Element> roots = XOMTools.getDescendantElementsWithTagName(word, ROOT_EL);
			if (roots.size() >1){
				throw new ComponentGenerationException("Multiple roots, but only 0 or 1 were expected. Found: " +roots.size());
			}
			List<Element> substituents = XOMTools.getDescendantElementsWithTagName(word, SUBSTITUENT_EL);
			List<Element> substituentsAndRoot = OpsinTools.combineElementLists(substituents, roots);
			List<Element> brackets =  XOMTools.getDescendantElementsWithTagName(word, BRACKET_EL);
			List<Element> substituentsAndRootAndBrackets =OpsinTools.combineElementLists(substituentsAndRoot, brackets);
			List<Element> groups =  XOMTools.getDescendantElementsWithTagName(word, GROUP_EL);

			for (Element group : groups) {
				Fragment thisFrag = resolveGroup(state, group);
				processChargeAndOxidationNumberSpecification(group, thisFrag);//e.g. mercury(2+) or mercury(II)
				state.xmlFragmentMap.put(group, thisFrag);
			}
			
			for (int j = substituents.size() -1; j >=0; j--) {
				Element substituent = substituents.get(j);
				boolean removed = removeHydroSubstituents(state, substituent);//this REMOVES substituents just containing hydro/dehydro/perhydro elements and moves these elements in front of an appropriate ring
				if (removed){
					substituents.remove(j);
					substituentsAndRoot.remove(substituent);
					substituentsAndRootAndBrackets.remove(substituent);
				}
			}

			Element finalSubOrRootInWord =(Element) word.getChild(word.getChildElements().size()-1);
			while (!finalSubOrRootInWord.getLocalName().equals(ROOT_EL) && !finalSubOrRootInWord.getLocalName().equals(SUBSTITUENT_EL)){
				List<Element> children = XOMTools.getChildElementsWithTagNames(finalSubOrRootInWord, new String[]{ROOT_EL, SUBSTITUENT_EL, BRACKET_EL});
				if (children.size()==0){
					throw new ComponentGenerationException("Unable to find finalSubOrRootInWord");
				}
				finalSubOrRootInWord = children.get(children.size()-1);
			}
		
			for (Element subOrRootOrBracket : substituentsAndRootAndBrackets) {
				determineLocantMeaning(state, subOrRootOrBracket, finalSubOrRootInWord);
			}

			for (Element subOrRoot : substituentsAndRoot) {
				processMultipliers(subOrRoot);
				detectConjunctiveSuffixGroups(state, subOrRoot, groups);
				matchLocantsToDirectFeatures(state, subOrRoot);

				Elements groupsOfSubOrRoot = subOrRoot.getChildElements(GROUP_EL);
				Element lastGroupInSubOrRoot =groupsOfSubOrRoot.get(groupsOfSubOrRoot.size()-1);
				preliminaryProcessSuffixes(state, lastGroupInSubOrRoot, XOMTools.getChildElementsWithTagName(subOrRoot, SUFFIX_EL));
			}
			FunctionalReplacement.processAmideOrHydrazideFunctionalClassNomenclature(state, finalSubOrRootInWord, word);

			if (FunctionalReplacement.processPrefixFunctionalReplacementNomenclature(state, groups, substituents)){//true if functional replacement performed, 1 or more substituents will have been removed
				substituentsAndRoot = OpsinTools.combineElementLists(substituents, roots);
				substituentsAndRootAndBrackets =OpsinTools.combineElementLists(substituentsAndRoot, brackets);
			}

			for (Element subOrRoot : substituentsAndRoot) {
				processHW(state, subOrRoot);//hantzch-widman rings
				FusedRingBuilder.processFusedRings(state, subOrRoot);
				processFusedRingBridges(state, subOrRoot);
				assignElementSymbolLocants(state, subOrRoot);
				processRingAssemblies(state, subOrRoot);
				processPolyCyclicSpiroNomenclature(state, subOrRoot);
			}

			for (Element subOrRoot : substituentsAndRoot) {
				applyLambdaConvention(state, subOrRoot);
				handleMultiRadicals(state, subOrRoot);
			}

			//System.out.println(new XOMFormatter().elemToString(elem));
			addImplicitBracketsToAminoAcids(state, groups, brackets);
			findAndStructureImplictBrackets(state, substituents, brackets);

			substituentsAndRootAndBrackets =OpsinTools.combineElementLists(substituentsAndRoot, brackets);//findAndStructureImplictBrackets may have created new brackets

			for (Element subOrRoot : substituentsAndRoot) {
				matchLocantsToIndirectFeatures(state, subOrRoot);
				assignImplicitLocantsToDiTerminalSuffixes(state, subOrRoot);
				processConjunctiveNomenclature(state, subOrRoot);
				resolveSuffixes(state, subOrRoot.getFirstChildElement(GROUP_EL), XOMTools.getChildElementsWithTagName(subOrRoot, SUFFIX_EL));
			}

			moveErroneouslyPositionedLocantsAndMultipliers(brackets);//e.g. (tetramethyl)azanium == tetra(methyl)azanium
			List<Element> children = XOMTools.getChildElementsWithTagNames(word, new String[]{ROOT_EL, SUBSTITUENT_EL, BRACKET_EL});
			while (children.size()==1){
				children = XOMTools.getChildElementsWithTagNames(children.get(0), new String[]{ROOT_EL, SUBSTITUENT_EL, BRACKET_EL});
			}
			if (children.size()>0){
				assignLocantsToMultipliedRootIfPresent(state, children.get(children.size()-1));//multiplicative nomenclature e.g. methylenedibenzene or 3,4'-oxydipyridine
			}
			for (Element subBracketOrRoot : substituentsAndRootAndBrackets) {
				assignLocantsAndMultipliers(state, subBracketOrRoot);
			}
			processWordLevelMultiplierIfApplicable(state, word, wordCount);
			for (Element group : groups) {
				state.xmlFragmentMap.get(group).sortAtomListByLocant();
			}
		}
	}

	/**Resolves the contents of a group element
	 *
	 * @param group The group element
	 * @return The fragment specified by the group element.
	 * @throws StructureBuildingException If the group can't be built.
	 * @throws ComponentGenerationException
	 */
	static Fragment resolveGroup(BuildState state, Element group) throws StructureBuildingException, ComponentGenerationException {
		String groupType = group.getAttributeValue(TYPE_ATR);
		String groupSubType = group.getAttributeValue(SUBTYPE_ATR);
		String groupValue = group.getAttributeValue(VALUE_ATR);
		String groupValType = group.getAttributeValue(VALTYPE_ATR);
		Fragment thisFrag =null;
		if(groupValType.equals(SMILES_VALTYPE_VAL)) {
			if (group.getAttribute(LABELS_ATR)!=null){
				thisFrag = state.fragManager.buildSMILES(groupValue, groupType, groupSubType, group.getAttributeValue(LABELS_ATR));
			}
			else{
				thisFrag = state.fragManager.buildSMILES(groupValue, groupType, groupSubType, "");
			}
		} else if(groupValType.equals(DBKEY_VALTYPE_VAL)) {
			thisFrag = state.fragManager.buildCML(groupValue, groupType, groupSubType);
		}
		else{
			throw new StructureBuildingException("Group tag has bad or missing valType: " + group.toXML());
		}
		if (thisFrag ==null){
			throw new StructureBuildingException("null fragment returned from the following xml: " + group.toXML());
		}

		//processes groups like cymene and xylene whose structure is determined by the presence of a locant in front e.g. p-xylene
		processXyleneLikeNomenclature(state, group, thisFrag);

		FragmentTools.convertHighOrderBondsToSpareValencies(thisFrag);//only applied to cyclic bonds

		setFragmentDefaultInAtomIfSpecified(thisFrag, group);
		setFragmentFunctionalAtomsIfSpecified(group, thisFrag);

		return thisFrag;
	}
	
	/**
	 * Looks for the presence of DEFAULTINLOCANT_ATR and DEFAULTINID_ATR on the group and applies them to the fragment
	 * Also sets the default in atom for alkanes so that say methylethyl is prop-2-yl rather than propyl
	 * @param thisFrag
	 * @param group
	 * @throws StructureBuildingException
	 */
	private static void setFragmentDefaultInAtomIfSpecified(Fragment thisFrag, Element group) throws StructureBuildingException {
		String groupSubType = group.getAttributeValue(SUBTYPE_ATR);
		if (group.getAttribute(DEFAULTINLOCANT_ATR)!=null){//sets the atom at which substitution will occur to by default
			thisFrag.setDefaultInAtom(thisFrag.getAtomByLocantOrThrow(group.getAttributeValue(DEFAULTINLOCANT_ATR)));
		}
		else if (group.getAttribute(DEFAULTINID_ATR)!=null){
			thisFrag.setDefaultInAtom(thisFrag.getAtomByIDOrThrow(thisFrag.getIdOfFirstAtom() + Integer.parseInt(group.getAttributeValue(DEFAULTINID_ATR)) -1));
		}
		else if ("yes".equals(group.getAttributeValue(USABLEASJOINER_ATR)) && group.getAttribute(SUFFIXAPPLIESTO_ATR)==null){//makes linkers by default attach end to end
			int chainLength =thisFrag.getChainLength();
			if (chainLength >1){
				boolean connectEndToEndWithPreviousSub =true;
				if (groupSubType.equals(ALKANESTEM_SUBTYPE_VAL)){//don't do this if you the group is preceded by another alkaneStem e.g. methylethyl makes more sense as prop-2-yl rather than propyl
					Element previousSubstituent =(Element) XOMTools.getPreviousSibling(group.getParent());
					if (previousSubstituent!=null){
						Elements groups = previousSubstituent.getChildElements(GROUP_EL);
						if (groups.size()==1 && groups.get(0).getAttributeValue(SUBTYPE_ATR).equals(ALKANESTEM_SUBTYPE_VAL) && !groups.get(0).getAttributeValue(TYPE_ATR).equals(RING_TYPE_VAL)){
							connectEndToEndWithPreviousSub = false;
						}
					}
				}
				if (connectEndToEndWithPreviousSub){
					Element parent =(Element) group.getParent();
					while (parent.getLocalName().equals(BRACKET_EL)){
						parent = (Element) parent.getParent();
					}
					if (parent.getLocalName().equals(ROOT_EL)){
						Element previous = (Element) XOMTools.getPrevious(group);
						if (previous==null || !previous.getLocalName().equals(MULTIPLIER_EL)){
							connectEndToEndWithPreviousSub=false;
						}
					}
				}
				if (connectEndToEndWithPreviousSub){
					group.addAttribute(new Attribute(DEFAULTINID_ATR, Integer.toString(chainLength)));
					thisFrag.setDefaultInAtom(thisFrag.getAtomByLocantOrThrow(Integer.toString(chainLength)));
				}
			}
		}
	}
	
	/**
	 * Looks for the presence of FUNCTIONALIDS_ATR on the group and applies them to the fragment
	 * @param group
	 * @param thisFrag
	 * @throws StructureBuildingException
	 */
	private static void setFragmentFunctionalAtomsIfSpecified(Element group, Fragment thisFrag) throws StructureBuildingException {
		if (group.getAttribute(FUNCTIONALIDS_ATR)!=null){
			String[] functionalIDs = matchComma.split(group.getAttributeValue(FUNCTIONALIDS_ATR));
            for (String functionalID : functionalIDs) {
                thisFrag.addFunctionalAtom(thisFrag.getAtomByIDOrThrow(thisFrag.getIdOfFirstAtom() + Integer.parseInt(functionalID) - 1));
            }
		}
	}

	/**
	 * Checks for groups with the addGroup/addBond/addHeteroAtom attributes. For the addGroup attribute adds the group defined by the SMILES described within
	 * e.g. for xylene  this function would add two methyls. Xylene is initially generated using the structure of benzene!
	 * See tokenList dtd for more information on the syntax of these attributes if it is not clear from the code
	 * @param state
	 * @param group: The group element
	 * @param parentFrag: The fragment that has been generated from the group element
	 * @throws StructureBuildingException
	 * @throws ComponentGenerationException
	 */
	private static void processXyleneLikeNomenclature(BuildState state, Element group, Fragment parentFrag) throws StructureBuildingException, ComponentGenerationException {
		if(group.getAttribute(ADDGROUP_ATR)!=null) {
			String addGroupInformation=group.getAttributeValue(ADDGROUP_ATR);
			String[] groupsToBeAdded = matchSemiColon.split(addGroupInformation);//typically only one, but 2 in the case of xylene and quinones
			ArrayList<HashMap<String, String>> allGroupInformation = new ArrayList<HashMap<String, String>>();
            for (String groupToBeAdded : groupsToBeAdded) {//populate allGroupInformation list
                String[] tempArray = matchSpace.split(groupToBeAdded);
                HashMap<String, String> groupInformation = new HashMap<String, String>();
                if (tempArray.length != 2 && tempArray.length != 3) {
                    throw new ComponentGenerationException("malformed addGroup tag");
                }
                groupInformation.put("SMILES", tempArray[0]);
                if (tempArray[1].startsWith("id")) {
                    groupInformation.put("atomReferenceType", "id");
                    groupInformation.put("atomReference", tempArray[1].substring(2));
                } else if (tempArray[1].startsWith("locant")) {
                    groupInformation.put("atomReferenceType", "locant");
                    groupInformation.put("atomReference", tempArray[1].substring(6));
                } else {
                    throw new ComponentGenerationException("malformed addGroup tag");
                }
                if (tempArray.length == 3) {//labels may optionally be specified for the group to be added
                    groupInformation.put("labels", tempArray[2]);
                }
                allGroupInformation.add(groupInformation);
            }
			Element previousEl =(Element) XOMTools.getPreviousSibling(group);
			if (previousEl !=null && previousEl.getLocalName().equals(LOCANT_EL)){//has the name got specified locants to override the default ones
				List<String> locantValues =StringTools.arrayToList(matchComma.split(previousEl.getValue()));
				if (locantValues.size()==groupsToBeAdded.length || locantValues.size() +1 ==groupsToBeAdded.length){//one locant can be implicit in some cases
					boolean assignlocants =true;
					if (locantValues.size()!=groupsToBeAdded.length){
						//check that the firstGroup by default will be added to the atom with locant 1. If this is not the case then as many locants as there were groups should of been specified
						//or no locants should have been specified, which is what will be assumed (i.e. the locants will be left unassigned)
						HashMap<String, String> groupInformation =allGroupInformation.get(0);
						String locant;
						if (groupInformation.get("atomReferenceType").equals("locant")){
							locant =parentFrag.getAtomByLocantOrThrow(groupInformation.get("atomReference")).getFirstLocant();
						}
						else if (groupInformation.get("atomReferenceType").equals("id") ){
							locant =parentFrag.getAtomByIDOrThrow(parentFrag.getIdOfFirstAtom() + Integer.parseInt(groupInformation.get("atomReference")) -1 ).getFirstLocant();
						}
						else{
							throw new ComponentGenerationException("malformed addGroup tag");
						}
						if (locant ==null || !locant.equals("1")){
							assignlocants=false;
						}
					}
					if (assignlocants){
						for (int i = groupsToBeAdded.length -1; i >=0 ; i--) {
							//if less locants than expected are specified the locants of only the later groups will be changed
							//e.g. 4-xylene will transform 1,2-xylene to 1,4-xylene
							HashMap<String, String> groupInformation =allGroupInformation.get(i);
							if (locantValues.size() >0){
								groupInformation.put("atomReferenceType", "locant");
								groupInformation.put("atomReference", locantValues.get(locantValues.size()-1));
								locantValues.remove(locantValues.size()-1);
							}
							else{
								break;
							}
						}
						previousEl.detach();
					}
				}
			}

			for (int i = 0; i < groupsToBeAdded.length; i++) {
				HashMap<String, String> groupInformation =allGroupInformation.get(i);
				String smilesOfGroupToBeAdded = groupInformation.get("SMILES");
				Fragment newFrag;
				if (groupInformation.get("labels")!=null){
					newFrag = state.fragManager.buildSMILES(smilesOfGroupToBeAdded, parentFrag.getType(), parentFrag.getSubType(), groupInformation.get("labels"));
				}
				else{
					newFrag = state.fragManager.buildSMILES(smilesOfGroupToBeAdded, parentFrag.getType(), parentFrag.getSubType(), NONE_LABELS_VAL);
				}

				Atom atomOnParentFrag =null;
				if (groupInformation.get("atomReferenceType").equals("locant")){
					atomOnParentFrag=parentFrag.getAtomByLocantOrThrow(groupInformation.get("atomReference"));
				}
				else if (groupInformation.get("atomReferenceType").equals("id") ){
					atomOnParentFrag= parentFrag.getAtomByIDOrThrow(parentFrag.getIdOfFirstAtom() + Integer.parseInt(groupInformation.get("atomReference")) -1);
				}
				else{
					throw new ComponentGenerationException("malformed addGroup tag");
				}
				if (newFrag.getOutAtoms().size() >1){
					throw new ComponentGenerationException("too many outAtoms on group to be added");
				}
				if (newFrag.getOutAtoms().size() ==1) {
					OutAtom newFragOutAtom = newFrag.getOutAtom(0);
					newFrag.removeOutAtom(newFragOutAtom);
					state.fragManager.incorporateFragment(newFrag, newFragOutAtom.getAtom().getID(), parentFrag, atomOnParentFrag.getID(), newFragOutAtom.getValency());
				}
				else{
					Atom atomOnNewFrag = newFrag.getDefaultInAtom();
					state.fragManager.incorporateFragment(newFrag, atomOnNewFrag.getID(), parentFrag, atomOnParentFrag.getID(), 1);
				}
			}
		}

		if(group.getAttributeValue(ADDHETEROATOM_ATR)!=null) {
			String addHeteroAtomInformation=group.getAttributeValue(ADDHETEROATOM_ATR);
			String[] heteroAtomsToBeAdded = matchSemiColon.split(addHeteroAtomInformation);
			ArrayList<HashMap<String, String>> allHeteroAtomInformation = new ArrayList<HashMap<String, String>>();
            for (String heteroAtomToBeAdded : heteroAtomsToBeAdded) {//populate allHeteroAtomInformation list
                String[] tempArray = matchSpace.split(heteroAtomToBeAdded);
                HashMap<String, String> heteroAtomInformation = new HashMap<String, String>();
                if (tempArray.length != 2) {
                    throw new ComponentGenerationException("malformed addHeteroAtom tag");
                }
                heteroAtomInformation.put("SMILES", tempArray[0]);
                if (tempArray[1].startsWith("id")) {
                    heteroAtomInformation.put("atomReferenceType", "id");
                    heteroAtomInformation.put("atomReference", tempArray[1].substring(2));
                } else if (tempArray[1].startsWith("locant")) {
                    heteroAtomInformation.put("atomReferenceType", "locant");
                    heteroAtomInformation.put("atomReference", tempArray[1].substring(6));
                } else {
                    throw new ComponentGenerationException("malformed addHeteroAtom tag");
                }
                allHeteroAtomInformation.add(heteroAtomInformation);
            }
			Element previousEl =(Element) XOMTools.getPreviousSibling(group);
			if (previousEl !=null && previousEl.getLocalName().equals(LOCANT_EL)){//has the name got specified locants to override the default ones
				List<String> locantValues =StringTools.arrayToList(matchComma.split(previousEl.getValue()));
				if (locantValues.size() ==heteroAtomsToBeAdded.length){
					for (int i = heteroAtomsToBeAdded.length -1; i >=0 ; i--) {//all heteroatoms must have a locant or default locants will be used
						HashMap<String, String> groupInformation =allHeteroAtomInformation.get(i);
						groupInformation.put("atomReferenceType", "locant");
						groupInformation.put("atomReference", locantValues.get(locantValues.size()-1));
						locantValues.remove(locantValues.size()-1);
					}
					previousEl.detach();
				}
			}

			for (int i = 0; i < heteroAtomsToBeAdded.length; i++) {
				HashMap<String, String> heteroAtomInformation =allHeteroAtomInformation.get(i);
				Atom atomOnParentFrag =null;
				if (heteroAtomInformation.get("atomReferenceType").equals("locant")){
					atomOnParentFrag=parentFrag.getAtomByLocantOrThrow(heteroAtomInformation.get("atomReference"));
				}
				else if (heteroAtomInformation.get("atomReferenceType").equals("id") ){
					atomOnParentFrag= parentFrag.getAtomByIDOrThrow(parentFrag.getIdOfFirstAtom() + Integer.parseInt(heteroAtomInformation.get("atomReference")) -1);
				}
				else{
					throw new ComponentGenerationException("malformed addHeteroAtom tag");
				}
				String atomSymbol = heteroAtomInformation.get("SMILES");
				if(atomSymbol.startsWith("[")) {
					Fragment f = state.fragManager.buildSMILES(atomSymbol);
					Atom referenceAtom = f.getAtomList().get(0);
					atomSymbol =referenceAtom.getElement();
					atomOnParentFrag.setCharge(referenceAtom.getCharge());
					state.fragManager.removeFragment(f);
				}
				atomOnParentFrag.setElement(atomSymbol);
			}
		}

		if(group.getAttributeValue(ADDBOND_ATR)!=null && !HANTZSCHWIDMAN_SUBTYPE_VAL.equals(group.getAttributeValue(SUBTYPE_ATR))) {//HW add bond is handled later
			String addBondInformation=group.getAttributeValue(ADDBOND_ATR);
			String[] bondsToBeAdded = matchSemiColon.split(addBondInformation);
			ArrayList<HashMap<String, String>> allBondInformation = new ArrayList<HashMap<String, String>>();
            for (String bondToBeAdded : bondsToBeAdded) {//populate allBondInformation list
                String[] tempArray = matchSpace.split(bondToBeAdded);
                HashMap<String, String> bondInformation = new HashMap<String, String>();
                if (tempArray.length != 2) {
                    throw new ComponentGenerationException("malformed addBond tag");
                }
                bondInformation.put("bondOrder", tempArray[0]);
                if (tempArray[1].startsWith("id")) {
                    bondInformation.put("atomReferenceType", "id");
                    bondInformation.put("atomReference", tempArray[1].substring(2));
                } else if (tempArray[1].startsWith("locant")) {
                    bondInformation.put("atomReferenceType", "locant");
                    bondInformation.put("atomReference", tempArray[1].substring(6));
                } else {
                    throw new ComponentGenerationException("malformed addBond tag");
                }
                allBondInformation.add(bondInformation);
            }
			Element previousEl =(Element) XOMTools.getPreviousSibling(group);
			if (previousEl !=null && previousEl.getLocalName().equals(LOCANT_EL)){//has the name got specified locants to override the default ones
				List<String> locantValues =StringTools.arrayToList(matchComma.split(previousEl.getValue()));
				if (locantValues.size() ==bondsToBeAdded.length){
					for (int i = bondsToBeAdded.length -1; i >=0 ; i--) {//all bond order changes must have a locant or default locants will be used
						HashMap<String, String> bondInformation =allBondInformation.get(i);
						bondInformation.put("atomReferenceType", "locant");
						bondInformation.put("atomReference", locantValues.get(locantValues.size()-1));
						locantValues.remove(locantValues.size()-1);
					}
					previousEl.detach();
				}
			}

			for (int i = 0; i < bondsToBeAdded.length; i++) {
				HashMap<String, String> bondInformation =allBondInformation.get(i);
				Atom atomOnParentFrag =null;
				if (bondInformation.get("atomReferenceType").equals("locant")){
					atomOnParentFrag=parentFrag.getAtomByLocantOrThrow(bondInformation.get("atomReference"));
				}
				else if (bondInformation.get("atomReferenceType").equals("id") ){
					atomOnParentFrag= parentFrag.getAtomByIDOrThrow(parentFrag.getIdOfFirstAtom() + Integer.parseInt(bondInformation.get("atomReference")) -1);
				}
				else{
					throw new ComponentGenerationException("malformed addBond tag");
				}
				FragmentTools.unsaturate(atomOnParentFrag, Integer.parseInt(bondInformation.get("bondOrder")) , parentFrag);
			}
		}
	}

	private void processChargeAndOxidationNumberSpecification(Element group, Fragment frag) throws StructureBuildingException {
		Element nextEl = (Element) XOMTools.getNextSibling(group);
		if (nextEl!=null){
			if(nextEl.getLocalName().equals(CHARGESPECIFIER_EL)){
				frag.getFirstAtom().setCharge(Integer.parseInt(nextEl.getAttributeValue(VALUE_ATR)));
				nextEl.detach();
			}
			if(nextEl.getLocalName().equals(OXIDATIONNUMBERSPECIFIER_EL)){
				frag.getFirstAtom().setProperty(Atom.OXIDATION_NUMBER, Integer.parseInt(nextEl.getAttributeValue(VALUE_ATR)));
				nextEl.detach();
			}
		}
	}


	/**
	 * Removes substituents which are just a hydro/dehydro/perhydro element and moves their contents to be in front of the next in scope ring
	 * @param state
	 * @param substituent
	 * @return true is the substituent was a hydro substituent and hence was removed
	 * @throws ComponentGenerationException
	 */
	private boolean removeHydroSubstituents(BuildState state, Element substituent) throws ComponentGenerationException {
		Elements hydroElements = substituent.getChildElements(HYDRO_EL);
		if (hydroElements.size() > 0 && substituent.getChildElements(GROUP_EL).size()==0){
			Element hydroSubstituent = substituent;
			if (hydroElements.size()!=1){
				throw new ComponentGenerationException("Unexpected number of hydro elements found in substituent");
			}
			Element hydroElement = hydroElements.get(0);
			String hydroValue = hydroElement.getValue();
			if (hydroValue.equals("hydro") || hydroValue.equals("dehydro")){
				Element multiplier = (Element) XOMTools.getPreviousSibling(hydroElement);
				if (multiplier == null || !multiplier.getLocalName().equals(MULTIPLIER_EL) ){
					throw new ComponentGenerationException("Multiplier expected but not found before hydro subsituent");
				}
				if (Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR)) %2 !=0){
					throw new ComponentGenerationException("Hydro/dehydro can only be added in pairs but multiplier was odd: " + multiplier.getAttributeValue(VALUE_ATR));
				}
			}
			Element targetRing =null;
			Node nextSubOrRootOrBracket = XOMTools.getNextSibling(hydroSubstituent);
			//first check adjacent substituent/root. If the hydroelement has one locant or the ring is locantless then we can assume the hydro is acting as a nondetachable prefix
			Element potentialRing =((Element)nextSubOrRootOrBracket).getFirstChildElement(GROUP_EL);
			if (potentialRing!=null && containsCyclicAtoms(state, potentialRing)){
				Element possibleLocantInFrontOfHydro = XOMTools.getPreviousSiblingIgnoringCertainElements(hydroElement, new String[]{MULTIPLIER_EL});
				if (possibleLocantInFrontOfHydro !=null && possibleLocantInFrontOfHydro.getLocalName().equals(LOCANT_EL) && matchComma.split(possibleLocantInFrontOfHydro.getValue()).length==1){
					//e.g.4-decahydro-1-naphthalenyl
					targetRing =potentialRing;
				}
				else{
					Element possibleLocantInFrontOfRing =(Element) XOMTools.getPreviousSibling(potentialRing, LOCANT_EL);
					if (possibleLocantInFrontOfRing !=null){
						if (potentialRing.getAttribute(FRONTLOCANTSEXPECTED_ATR)!=null){//check whether the group was expecting a locant e.g. 2-furyl
							String locantValue = possibleLocantInFrontOfRing.getValue();
							String[] expectedLocants = matchComma.split(potentialRing.getAttributeValue(FRONTLOCANTSEXPECTED_ATR));
							for (String expectedLocant : expectedLocants) {
								if (locantValue.equals(expectedLocant)){
									targetRing =potentialRing;
									break;
								}
							}
						}
						//check whether the group is a HW system e.g. 1,3-thiazole
						if (potentialRing.getAttributeValue(SUBTYPE_ATR).equals(HANTZSCHWIDMAN_SUBTYPE_VAL)){
							String locantValue = possibleLocantInFrontOfRing.getValue();
							int locants = matchComma.split(locantValue).length;
							int heteroCount = 0;
							Element currentElem =  (Element) XOMTools.getNextSibling(possibleLocantInFrontOfRing);
							while(!currentElem.equals(potentialRing)){
								if(currentElem.getLocalName().equals(HETEROATOM_EL)) {
									heteroCount++;
								} else if (currentElem.getLocalName().equals(MULTIPLIER_EL)){
									heteroCount += Integer.parseInt(currentElem.getAttributeValue(VALUE_ATR)) -1;
								}
								currentElem = (Element)XOMTools.getNextSibling(currentElem);
							}
							if (heteroCount==locants){//number of locants must match number
								targetRing =potentialRing;
							}
						}
						//check whether the group is a benzofused ring e.g. 1,4-benzodioxin
						if (FUSIONRING_SUBTYPE_VAL.equals(potentialRing.getAttributeValue(SUBTYPE_ATR)) && 
								(potentialRing.getValue().equals("benzo")|| potentialRing.getValue().equals("benz")) &&
								!((Element)XOMTools.getNextSibling(potentialRing)).getLocalName().equals(FUSION_EL)){
							targetRing =potentialRing;
						}
					}
					else{
						targetRing =potentialRing;
					}
				}
			}
	
			//that didn't match so the hydro appears to be a detachable prefix. detachable prefixes attach in preference to the rightmost applicable group so search any remaining substituents/roots from right to left
			if (targetRing ==null){
				Element nextSubOrRootOrBracketfromLast = (Element) hydroSubstituent.getParent().getChild(hydroSubstituent.getParent().getChildCount()-1);//the last sibling
				while (!nextSubOrRootOrBracketfromLast.equals(hydroSubstituent)){
					potentialRing = nextSubOrRootOrBracketfromLast.getFirstChildElement(GROUP_EL);
					if (potentialRing!=null && containsCyclicAtoms(state, potentialRing)){
						targetRing =potentialRing;
						break;
					}
					else{
						nextSubOrRootOrBracketfromLast = (Element) XOMTools.getPreviousSibling(nextSubOrRootOrBracketfromLast);
					}
				}
			}
			if (targetRing ==null){
				throw new ComponentGenerationException("Cannot find ring for hydro substituent to apply to");
			}
			//move the children of the hydro substituent
			Elements children =hydroSubstituent.getChildElements();
			for (int i = children.size()-1; i >=0 ; i--) {
				Element child =children.get(i);
				if (!child.getLocalName().equals(HYPHEN_EL)){
					child.detach();
					targetRing.getParent().insertChild(child, 0);
				}
			}
			hydroSubstituent.detach();
			return true;
		}
		return false;
	}


	private boolean containsCyclicAtoms(BuildState state, Element potentialRing) {
		Fragment potentialRingFrag = state.xmlFragmentMap.get(potentialRing);
		List<Atom> atomList = potentialRingFrag.getAtomList();
		for (Atom atom : atomList) {
			if (atom.getAtomIsInACycle()){
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks for agreement between the number of locants and multipliers.
	 * If a locant element contains multiple elements and is not next to a multiplier the various cases where this is the case will be checked for
	 * This may result in a locant being moved if it is more convenient for subsequent processing
	 * @param state
	 * @param subOrBracketOrRoot The substituent/root/bracket to looks for locants in.
	 * @param finalSubOrRootInWord : used to check if a locant is referring to the root as in multiplicative nomenclature
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void determineLocantMeaning(BuildState state, Element subOrBracketOrRoot, Element finalSubOrRootInWord) throws StructureBuildingException, ComponentGenerationException {
		List<Element> locants = XOMTools.getChildElementsWithTagName(subOrBracketOrRoot, LOCANT_EL);
		Element group =subOrBracketOrRoot.getFirstChildElement(GROUP_EL);//will be null if element is a bracket
		for (Element locant : locants) {
			String [] locantValues = matchComma.split(locant.getValue());
			if(locantValues.length > 1) {
				Element afterLocant = (Element)XOMTools.getNextSibling(locant);
				int structuralBracketDepth = 0;
				Element multiplierEl = null;
				while (afterLocant !=null){
					String elName = afterLocant.getLocalName();
					if (elName.equals(STRUCTURALOPENBRACKET_EL)){
						structuralBracketDepth++;
					}
					else if (elName.equals(STRUCTURALCLOSEBRACKET_EL)){
						structuralBracketDepth--;
					}
					if (structuralBracketDepth!=0){
						afterLocant = (Element)XOMTools.getNextSibling(afterLocant);
						continue;
					}
					if(elName.equals(LOCANT_EL)) {
						break;
					}
					else if (elName.equals(MULTIPLIER_EL)){
						if (locantValues.length == Integer.parseInt(afterLocant.getAttributeValue(VALUE_ATR))){
							if (afterLocant.equals(XOMTools.getNextSiblingIgnoringCertainElements(locant, new String[]{HYDROGEN_EL}))){
								//direct locant, typical case. An exception is made for indicated hydrogen e.g. 1,2,4-1H-triazole
								multiplierEl = afterLocant;
								break;
							}
							else{
								Element afterMultiplier = (Element) XOMTools.getNextSibling(afterLocant);
								if (afterMultiplier!=null && (afterMultiplier.getLocalName().equals(SUFFIX_EL) || afterMultiplier.getLocalName().equals(INFIX_EL)
										|| afterMultiplier.getLocalName().equals(UNSATURATOR_EL) || afterMultiplier.getLocalName().equals(GROUP_EL))){
									multiplierEl = afterLocant; //indirect locant
									break;
								}
							}
						}
						if (afterLocant.equals(XOMTools.getNextSibling(locant))){//if nothing better can be found report this as a locant/multiplier mismatch
							multiplierEl = afterLocant;
						}
					}
					afterLocant = (Element)XOMTools.getNextSibling(afterLocant);
				}
				if(multiplierEl != null) {
					if(Integer.parseInt(multiplierEl.getAttributeValue(VALUE_ATR)) == locantValues.length ) {
						// number of locants and multiplier agree
						boolean locantModified =false;//did determineLocantMeaning do something?
						if (locantValues[locantValues.length-1].endsWith("'") && group!=null && subOrBracketOrRoot.indexOf(group) > subOrBracketOrRoot.indexOf(locant)){//quite possible that this is referring to a multiplied root
							if (group.getAttribute(OUTIDS_ATR)!=null && matchComma.split(group.getAttributeValue(OUTIDS_ATR)).length>1){
								locantModified = checkSpecialLocantUses(state, locant, locantValues, finalSubOrRootInWord);
							}
							else{
								Element afterGroup = (Element)XOMTools.getNextSibling(group);
								int inlineSuffixCount =0;
								int multiplier=1;
								while (afterGroup !=null){
									if(afterGroup.getLocalName().equals(MULTIPLIER_EL)){
										multiplier =Integer.parseInt(afterGroup.getAttributeValue(VALUE_ATR));
									}
									else if(afterGroup.getLocalName().equals(SUFFIX_EL) && afterGroup.getAttributeValue(TYPE_ATR).equals(INLINE_TYPE_VAL)){
										inlineSuffixCount +=(multiplier);
										multiplier=1;
									}
									afterGroup = (Element)XOMTools.getNextSibling(afterGroup);
								}
								if (inlineSuffixCount >=2){
									locantModified = checkSpecialLocantUses(state, locant, locantValues, finalSubOrRootInWord);
								}
							}
						}
						if (!locantModified && !XOMTools.getNextSibling(locant).equals(multiplierEl)){//the locants apply indirectly the multiplier e.g. 2,3-butandiol
							//move the locant to be next to the multiplier.
							locant.detach();
							XOMTools.insertBefore(multiplierEl, locant);
						}
					} else {
						if(!checkSpecialLocantUses(state, locant, locantValues, finalSubOrRootInWord)) {
							throw new ComponentGenerationException("Mismatch between locant and multiplier counts (" + Integer.toString(locantValues.length) + " and " + multiplierEl.getAttributeValue(VALUE_ATR) + "):" + locant.toXML());
						}
					}
				} else {
					/* Multiple locants without a multiplier */
					if(!checkSpecialLocantUses(state, locant, locantValues, finalSubOrRootInWord)) {
						throw new ComponentGenerationException("Multiple locants without a multiplier: " + locant.toXML());
					}
				}
			}
		}
	}


	/**Looks for Hantzch-Widman systems, and sees if the number of locants
	 * agrees with the number of heteroatoms.
	 * If this is not the case alternative possibilities are tested:
	 * 	The locants could be intended to indicate the position of outAtoms e.g. 1,4-phenylene
	 * 	The locants could be intended to indicate the attachement points of the root groups in multiplicative nomenclature e.g. 4,4'-methylenedibenzoic acid
	 * @param state
	 * @param locant The element corresponding to the locant group to be tested
	 * @param locantValues The locant values;
	 * @param finalSubOrRootInWord : used to check if a locant is referring to the root as in multiplicative nomenclatures)
	 * @return true if there's a HW system, and agreement; or if the locants conform to one of the alternative possibilities, otherwise false.
	 * @throws StructureBuildingException
	 */
	private boolean checkSpecialLocantUses(BuildState state, Element locant, String[] locantValues, Element finalSubOrRootInWord) throws StructureBuildingException {
		int count =locantValues.length;
		Element currentElem = (Element)XOMTools.getNextSibling(locant);
		int heteroCount = 0;
		int multiplierValue = 1;
		while(currentElem != null && !currentElem.getLocalName().equals(GROUP_EL)){
			if(currentElem.getLocalName().equals(HETEROATOM_EL)) {
				heteroCount+=multiplierValue;
				multiplierValue =1;
			} else if (currentElem.getLocalName().equals(MULTIPLIER_EL)){
				multiplierValue = Integer.parseInt(currentElem.getAttributeValue(VALUE_ATR));
			}
			else{
				break;
			}
			currentElem = (Element)XOMTools.getNextSibling(currentElem);
		}
		if(currentElem != null && currentElem.getLocalName().equals(GROUP_EL)){
			if (currentElem.getAttributeValue(SUBTYPE_ATR).equals(HANTZSCHWIDMAN_SUBTYPE_VAL)) {
				if(heteroCount == count) {
					return true;
				} else if (heteroCount > 1){
					return false;//there is a case where locants don't apply to heteroatoms in a HW system, but in that case only one locant is expected so this function would not be called
				}
			}
			if (count ==2 && EPOXYLIKE_SUBTYPE_VAL.equals(currentElem.getAttributeValue(SUBTYPE_ATR)) && !locantValues[count-1].endsWith("'")){
				return true;
			}
			else if (heteroCount==0 && currentElem.getAttribute(OUTIDS_ATR)!=null ) {//e.g. 1,4-phenylene
				String[] outIDs = matchComma.split(currentElem.getAttributeValue(OUTIDS_ATR), -1);
				Fragment groupFragment =state.xmlFragmentMap.get(currentElem);
				if (count ==outIDs.length && groupFragment.getAtomList().size()>1){//things like oxy do not need to have their outIDs specified
					int idOfFirstAtomInFrag =groupFragment.getIdOfFirstAtom();
					boolean foundLocantNotPresentOnFragment = false;
					for (int i = outIDs.length-1; i >=0; i--) {
						Atom a =groupFragment.getAtomByLocant(locantValues[i]);
						if (a==null){
							foundLocantNotPresentOnFragment = true;
							break;
						}
						outIDs[i]=Integer.toString(a.getID() -idOfFirstAtomInFrag +1);//convert to relative id
					}
					if (!foundLocantNotPresentOnFragment){
						currentElem.getAttribute(OUTIDS_ATR).setValue(StringTools.arrayToString(outIDs, ","));
						locant.detach();
						return true;
					}
				}
			}
			else if(currentElem.getValue().equals("benz") || currentElem.getValue().equals("benzo")){
				Node potentialGroupAfterBenzo = XOMTools.getNextSibling(currentElem, GROUP_EL);//need to make sure this isn't benzyl
				if (potentialGroupAfterBenzo!=null){
					return true;//e.g. 1,2-benzothiazole
				}
			}
		}
		if(currentElem != null && currentElem.getLocalName().equals(POLYCYCLICSPIRO_EL)){
			return true;
		}
		if(currentElem != null && count==2 && currentElem.getLocalName().equals(FUSEDRINGBRIDGE_EL)){
			return true;
		}
		boolean detectedMultiplicativeNomenclature = detectMultiplicativeNomenclature(locant, locantValues, finalSubOrRootInWord);
		if (detectedMultiplicativeNomenclature){
			return true;
		}
		Element parentElem = (Element) locant.getParent();
		if (count==2 && parentElem.getLocalName().equals(BRACKET_EL)){//e.g. 3,4-(dichloromethylenedioxy) this is changed to (dichloro3,4-methylenedioxy)
			List<Element> substituents = XOMTools.getChildElementsWithTagName(parentElem, SUBSTITUENT_EL);
			if (substituents.size()>0){
				Element finalSub  = substituents.get(substituents.size()-1);
				Element group = finalSub.getFirstChildElement(GROUP_EL);
				if (EPOXYLIKE_SUBTYPE_VAL.equals(group.getAttributeValue(SUBTYPE_ATR))){
					locant.detach();
					XOMTools.insertBefore(group, locant);
					return true;
				}
			}
		}
		
		return false;
	}


	/**
	 * Detects multiplicative nomenclature. If it does then the locant will be moved, changed to a multiplicative locant and true will be returned
	 * @param locant
	 * @param locantValues
	 * @param finalSubOrRootInWord
	 * @return
	 */
	private boolean detectMultiplicativeNomenclature(Element locant, String[] locantValues, Element finalSubOrRootInWord) {
		int count =locantValues.length;
		Element multiplier =(Element) finalSubOrRootInWord.getChild(0);
		if (((Element)finalSubOrRootInWord.getParent()).getLocalName().equals(BRACKET_EL)){//e.g. 1,1'-ethynediylbis(1-cyclopentanol)
			if (!multiplier.getLocalName().equals(MULTIPLIER_EL)){
				multiplier =(Element) finalSubOrRootInWord.getParent().getChild(0);
			}
			else{
				Element elAfterMultiplier = (Element) XOMTools.getNextSibling(multiplier);
				String elName = elAfterMultiplier.getLocalName();
				if (elName.equals(HETEROATOM_EL) || (elName.equals(HYDRO_EL) && !elAfterMultiplier.getValue().startsWith("per"))|| elName.equals(FUSEDRINGBRIDGE_EL)) {
					multiplier =(Element) finalSubOrRootInWord.getParent().getChild(0);
				}
			}
		}
		Node commonParent =locant.getParent().getParent();//this should be a common parent of the multiplier in front of the root. If it is not, then this locant is in a different scope
		Node parentOfMultiplier =multiplier.getParent();
		while (parentOfMultiplier!=null){
			if (commonParent.equals(parentOfMultiplier)){
				if (locantValues[count-1].endsWith("'")  &&
						multiplier.getLocalName().equals(MULTIPLIER_EL) && !((Element)XOMTools.getNextSibling(multiplier)).getLocalName().equals(MULTIPLICATIVELOCANT_EL) &&
						Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR)) == count ){//multiplicative nomenclature
					locant.setLocalName(MULTIPLICATIVELOCANT_EL);
					locant.detach();
					XOMTools.insertAfter(multiplier, locant);
					return true;
				}
			}
			parentOfMultiplier=parentOfMultiplier.getParent();
		}
		return false;
	}

	/** Look for multipliers, and multiply out suffixes/unsaturators/heteroatoms/hydros.
	 * Locants are assigned if the number of locants matches the multiplier
	 * associated with them. Eg. triol - > ololol.
	 * Note that infix multiplication is handled seperately as resolution of suffixes is required to perform this unambiguously
	 * @param subOrRoot The substituent/root to looks for multipliers in.
	 */
	private void processMultipliers(Element subOrRoot) {
		List<Element> multipliers = XOMTools.getChildElementsWithTagName(subOrRoot, MULTIPLIER_EL);
		for (Element multiplier : multipliers) {
			Element possibleLocant =(Element)XOMTools.getPreviousSibling(multiplier);
			String[] locants = null;
			if (possibleLocant !=null && possibleLocant.getLocalName().equals(LOCANT_EL)){
				locants = matchComma.split(possibleLocant.getValue());
			}
			Element featureToMultiply = (Element)XOMTools.getNextSibling(multiplier);
			String nextName = featureToMultiply.getLocalName();
			if(nextName.equals(UNSATURATOR_EL) ||
					nextName.equals(SUFFIX_EL) ||
					nextName.equals(HETEROATOM_EL) ||
					nextName.equals(HYDRO_EL)) {
				int mvalue = Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR));
				featureToMultiply.addAttribute(new Attribute(MULTIPLIED_ATR, "multiplied"));
				for(int i= mvalue -1; i >=1; i--) {
					Element newElement = new Element(featureToMultiply);
					if (locants !=null && locants.length==mvalue){
						newElement.addAttribute(new Attribute(LOCANT_ATR, locants[i]));
					}
					XOMTools.insertAfter(featureToMultiply, newElement);
				}
				multiplier.detach();
				if (locants !=null && locants.length==mvalue){
					featureToMultiply.addAttribute(new Attribute(LOCANT_ATR, locants[0]));
					possibleLocant.detach();
				}
			}
		}
	}


	/**
	 * Converts group elements that are identified as being conjunctive suffixes to CONJUNCTIVESUFFIXGROUP_EL
	 * and labels them appropriately. Any suffixes that the conjunctive suffix may have are resolved onto it
	 * @param state
	 * @param subOrRoot
	 * @param allGroups
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void detectConjunctiveSuffixGroups(BuildState state, Element subOrRoot, List<Element> allGroups) throws ComponentGenerationException, StructureBuildingException {
		List<Element> groups = XOMTools.getChildElementsWithTagName(subOrRoot, GROUP_EL);
		if (groups.size()>1){
			List<Element> conjunctiveGroups = new ArrayList<Element>();
			Element ringGroup =null;
			for (int i = groups.size() -1 ; i >=0; i--) {
				Element group =groups.get(i);
				if (!group.getAttributeValue(TYPE_ATR).equals(RING_TYPE_VAL)){//e.g. the methanol in benzenemethanol.
					conjunctiveGroups.add(group);
				}
				else{
					ringGroup =group;
					break;
				}
			}
			if (conjunctiveGroups.size() ==0){
				return;
			}
			if (ringGroup ==null){
				throw new ComponentGenerationException("OPSIN bug: unable to find ring associated with conjunctive suffix group");
			}
			if (conjunctiveGroups.size()!=1){
				throw new ComponentGenerationException("OPSIN Bug: Two groups exactly should be present at this point when processing conjunctive nomenclature");
			}
			Element primaryConjunctiveGroup =conjunctiveGroups.get(0);
			Fragment primaryConjunctiveFrag = state.xmlFragmentMap.get(primaryConjunctiveGroup);
			//remove all locants
			List<Atom> atomList = primaryConjunctiveFrag.getAtomList();
			for (Atom atom : atomList) {
				atom.clearLocants();
			}
			List<Element> suffixes = new ArrayList<Element>();
			Element possibleSuffix = (Element) XOMTools.getNextSibling(primaryConjunctiveGroup);
			while (possibleSuffix !=null){
				if (possibleSuffix.getLocalName().equals(SUFFIX_EL)){
					suffixes.add(possibleSuffix);
				}
				possibleSuffix = (Element) XOMTools.getNextSibling(possibleSuffix);
			}
			preliminaryProcessSuffixes(state, primaryConjunctiveGroup, suffixes);
			resolveSuffixes(state, primaryConjunctiveGroup, suffixes);
            for (Element suffix : suffixes) {
                suffix.detach();
            }
			primaryConjunctiveGroup.setLocalName(CONJUNCTIVESUFFIXGROUP_EL);
			allGroups.remove(primaryConjunctiveGroup);
			
			Element possibleMultiplier = (Element) XOMTools.getPreviousSibling(primaryConjunctiveGroup);
			if (MULTIPLIER_EL.equals(possibleMultiplier.getLocalName())){
				int multiplier = Integer.parseInt(possibleMultiplier.getAttributeValue(VALUE_ATR));
				for (int i = 1; i < multiplier; i++) {
					Element conjunctiveSuffixGroup = new Element(primaryConjunctiveGroup);
					Fragment newFragment = state.fragManager.copyFragment(primaryConjunctiveFrag);
					state.xmlFragmentMap.put(conjunctiveSuffixGroup, newFragment);
					conjunctiveGroups.add(conjunctiveSuffixGroup);
					XOMTools.insertAfter(primaryConjunctiveGroup, conjunctiveSuffixGroup);
				}
				Element possibleLocant =(Element)XOMTools.getPreviousSibling(possibleMultiplier);
				possibleMultiplier.detach();
				if (possibleLocant.getLocalName().equals(LOCANT_EL)){
					String[] locants = matchComma.split(possibleLocant.getValue());
					if (locants.length!=multiplier){
						throw new ComponentGenerationException("mismatch between number of locants and multiplier in conjunctive nomenclature routine");
					}
					for (int i = 0; i < locants.length; i++) {
						conjunctiveGroups.get(i).addAttribute(new Attribute(LOCANT_ATR, locants[i]));
					}
					possibleLocant.detach();
				}
			}
		}
	}


	/** Match each locant to the next applicable "feature". Assumes that processLocants
	 * has done a good job and rejected cases where no match can be made.
	 * Handles cases where the locant is next to the feature it refers to
	 * @param state 
	 *
	 * @param subOrRoot The substituent/root to look for locants in.
	 * @throws ComponentGenerationException
	 */
	private void matchLocantsToDirectFeatures(BuildState state, Element subOrRoot) throws ComponentGenerationException {
		List<Element> locants =  XOMTools.getChildElementsWithTagName(subOrRoot, LOCANT_EL);
		List<Element> groups = XOMTools.getChildElementsWithTagName(subOrRoot, GROUP_EL);
		for (Element group : groups) {
			if (group.getAttributeValue(SUBTYPE_ATR).equals(HANTZSCHWIDMAN_SUBTYPE_VAL)){//handle Hantzch-widman systems
				if (group.getAttribute(ADDBOND_ATR)!=null){//special case for partunsatring
					//exception for where a locant is supposed to indicate the location of a double bond...
					Elements deltas = subOrRoot.getChildElements(DELTA_EL);
					if (deltas.size()==0){
						Element delta =new Element(DELTA_EL);
						Element appropriateLocant = XOMTools.getPreviousSiblingIgnoringCertainElements(group, new String[]{HETEROATOM_EL, MULTIPLIER_EL});
						if (appropriateLocant !=null && appropriateLocant.getLocalName().equals(LOCANT_EL) && matchComma.split(appropriateLocant.getValue()).length == 1){
							delta.appendChild(appropriateLocant.getValue());
							XOMTools.insertBefore(appropriateLocant, delta);
							appropriateLocant.detach();
							locants.remove(appropriateLocant);
						}
						else{
							delta.appendChild("");
							subOrRoot.insertChild(delta, 0);//no obvious attempt to set double bond position, potentially ambiguous, valency will be used to choose later
						}
					}
				}
				if (locants.size()>0 ){
					Element locantBeforeHWSystem = null;
					List<Element> heteroAtoms = new ArrayList<Element>();
					int indexOfGroup =subOrRoot.indexOf(group);
					for (int j = indexOfGroup -1; j >= 0; j--) {
						String elName=((Element)subOrRoot.getChild(j)).getLocalName();
						if (elName.equals(LOCANT_EL)){
							locantBeforeHWSystem = (Element)subOrRoot.getChild(j);
							break;
						}
						else if(elName.equals(HETEROATOM_EL)){
							Element heteroAtom = (Element)subOrRoot.getChild(j);
							heteroAtoms.add(heteroAtom);
							if (heteroAtom.getAttribute(LOCANT_ATR)!=null){//locants already assigned, assumedly by process multipliers
								break;
							}
						}
						else{
							break;
						}
					}
					Collections.reverse(heteroAtoms);
					if (locantBeforeHWSystem !=null){
						String[] locantValues = matchComma.split(locantBeforeHWSystem.getValue());
						//detect a solitary locant in front of a HW system and prevent it being assigned.
						//something like 1-aziridin-1-yl never means the N is at position 1 as it is at position 1 by convention
						//this special case is not applied to pseudo HW like systems e.g. [1]oxacyclotetradecine
						if (locantValues.length ==1 && state.xmlFragmentMap.get(group).getAtomList().size() <=10){
							locants.remove(locantBeforeHWSystem);//don't assign this locant
						}
						else {
							if (locantValues.length == heteroAtoms.size()){
								for (int j = 0; j < locantValues.length; j++) {
									String locantValue = locantValues[j];
									heteroAtoms.get(j).addAttribute(new Attribute(LOCANT_ATR, locantValue));
								}
								locantBeforeHWSystem.detach();
								locants.remove(locantBeforeHWSystem);
							}
							else if (heteroAtoms.size()>1){
								throw new ComponentGenerationException("Mismatch between number of locants and HW heteroatoms");
							}
						}
					}
				}
			}
		}
		assignSingleLocantsToAdjacentFeatures(locants);
	}


	/**
	 * Looks for a suffix/suffix/heteroatom/hydro element adjacent to the given locant
	 * and if the locant element describes just 1 locant asssigns it
	 * @param locants
	 */
	private void assignSingleLocantsToAdjacentFeatures(List<Element> locants) {
		for (Element locant : locants) {
			String[] locantValues = matchComma.split(locant.getValue());
			Element referent = (Element)XOMTools.getNextSibling(locant);
			if (referent!=null && locantValues.length==1){
				String refName = referent.getLocalName();
				//Only assigning locants to elements that were not created by a multiplier
				if(referent.getAttribute(LOCANT_ATR) == null && referent.getAttribute(MULTIPLIED_ATR) == null && (refName.equals(UNSATURATOR_EL) ||
						refName.equals(SUFFIX_EL) ||
						refName.equals(HETEROATOM_EL) ||
						refName.equals(CONJUNCTIVESUFFIXGROUP_EL) ||
						(refName.equals(HYDRO_EL) && !referent.getValue().startsWith("per") ))) {//not perhydro
					referent.addAttribute(new Attribute(LOCANT_ATR, locantValues[0]));
					locant.detach();
				}
			}
		}
	}


	/**
	 * Handles suffixes, passes them to resolveGroupAddingSuffixes.
	 * Processes the suffixAppliesTo command which multiplies a suffix and attaches the suffixes to the atoms described by the given IDs
	 * @param state
	 * @param group
	 * @param suffixes 
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void preliminaryProcessSuffixes(BuildState state, Element group, List<Element> suffixes) throws ComponentGenerationException, StructureBuildingException{
		Fragment suffixableFragment =state.xmlFragmentMap.get(group);

		boolean imideSpecialCase =false;
		if (group.getAttribute(SUFFIXAPPLIESTO_ATR)!=null){//typically a trivial polyAcid or aminoAcid
			imideSpecialCase = processSuffixAppliesTo(group, suffixes,suffixableFragment);
		}
		else{
			for (Element suffix : suffixes) {
				if (suffix.getAttribute(ADDITIONALVALUE_ATR)!=null){
					throw new ComponentGenerationException("suffix: " + suffix.getValue() + " used on an inappropriate group");
				}
			}
		}
		if (group.getAttribute(SUFFIXAPPLIESTOBYDEFAULT_ATR)!=null){
			applyDefaultLocantToSuffixIfPresent(group.getAttributeValue(SUFFIXAPPLIESTOBYDEFAULT_ATR), group, suffixableFragment);
		}
		List<Fragment> suffixFragments =resolveGroupAddingSuffixes(state, suffixes, suffixableFragment);
		state.xmlSuffixMap.put(group, suffixFragments);
		boolean suffixesResolved =false;
		if (group.getAttributeValue(TYPE_ATR).equals(CHALCOGENACIDSTEM_TYPE_VAL)){//merge the suffix into the chalcogen acid stem e.g sulfonoate needs to be one fragment for infix replacement
	    	resolveSuffixes(state, group, suffixes);
	    	suffixesResolved =true;
	    }
		processSuffixPrefixes(state, suffixes);//e.g. carbox amide
		FunctionalReplacement.processInfixFunctionalReplacementNomenclature(state, suffixes, suffixFragments);
		processConvertHydroxyGroupsToOutAtomsRule(state, suffixes, suffixableFragment);

		if (group.getValue().equals("oxal")){//oxalic acid is treated as a non carboxylic acid for the purposes of functional replacment. See P-65.2.3
			resolveSuffixes(state, group, suffixes);
	    	group.getAttribute(TYPE_ATR).setValue(NONCARBOXYLICACID_TYPE_VAL);
	    	suffixableFragment.setType(NONCARBOXYLICACID_TYPE_VAL);
	    	suffixesResolved =true;
	    }
		if (imideSpecialCase){//Pretty horrible hack to allow cyclic imides
			if (suffixes.size() !=2){
				throw new ComponentGenerationException("Expected two suffixes fragments for cyclic imide");
			}
			Atom nitrogen =null;
			for (Atom a : suffixFragments.get(0).getAtomList()) {
				if (a.getElement().equals("N")){//amide
					nitrogen =a;
				}
			}
			if (nitrogen ==null){
				throw new ComponentGenerationException("Nitrogen not found where nitrogen expected");
			}
			Atom carbon = suffixableFragment.getAtomByIDOrThrow(Integer.parseInt(suffixes.get(1).getAttributeValue(LOCANTID_ATR)));
			if (!carbon.getElement().equals("C")){
				throw new ComponentGenerationException("Carbon not found where carbon expected");
			}
			resolveSuffixes(state, group, suffixes);
			suffixesResolved = true;
			state.fragManager.createBond(nitrogen, carbon, 1);//join the N of the amide to the carbon of the acid to form the cyclic imide
		}
		if (suffixesResolved){
			//suffixes have already been resolved so need to be detached to avoid being passed to resolveSuffixes later
			for (int i = suffixes.size() -1; i>=0; i--) {
				Element suffix =suffixes.remove(i);
				suffix.detach();
			}
		}
		if (group.getAttribute(NUMBEROFFUNCTIONALATOMSTOREMOVE_ATR)!=null){
			int numberToRemove = Integer.parseInt(group.getAttributeValue(NUMBEROFFUNCTIONALATOMSTOREMOVE_ATR));
			if (numberToRemove > suffixableFragment.getFunctionalAtoms().size()){
				throw new ComponentGenerationException("Too many hydrogen for the number of positions on non carboxylic acid");
			}
			for (int i = 0; i< numberToRemove; i++) {
				Atom functionalAtom = suffixableFragment.removeFunctionalAtom(0).getAtom();
				functionalAtom.setCharge(0);
			}		
		}
	}


	private void applyDefaultLocantToSuffixIfPresent(String attributeValue, Element group, Fragment suffixableFragment) throws StructureBuildingException {
		Element suffix =OpsinTools.getNextNonChargeSuffix(group);
		if (suffix !=null){
			suffix.addAttribute(new Attribute(DEFAULTLOCANTID_ATR, Integer.toString(suffixableFragment.getIdOfFirstAtom() + Integer.parseInt(attributeValue) -1)));
		}
	}


	/**
	 * Processes the effects of the suffixAppliesTo attribute
	 * Returns true if an imide is detected
	 * @param group
	 * @param suffixes
	 * @param suffixableFragment
	 * @return
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private boolean processSuffixAppliesTo(Element group, List<Element> suffixes, Fragment suffixableFragment) throws ComponentGenerationException, StructureBuildingException {
		boolean imideSpecialCase =false;
		//suffixAppliesTo attribute contains instructions for number/positions of suffix
		//this is of the form comma sepeated ids with the number of ids corresponding to the number of instances of the suffix
		Element suffix =OpsinTools.getNextNonChargeSuffix(group);
		if (suffix ==null){
			throw new ComponentGenerationException("No suffix where suffix was expected");
		}
		else{
			if (suffixes.size()>1 && group.getAttributeValue(TYPE_ATR).equals(ACIDSTEM_TYPE_VAL)){
				throw new ComponentGenerationException("More than one suffix detected on trivial polyAcid. Not believed to be allowed");
			}
			String suffixInstruction =group.getAttributeValue(SUFFIXAPPLIESTO_ATR);
			String[] suffixInstructions = matchComma.split(suffixInstruction);
			boolean symmetricSuffixes =true;
			if (suffix.getAttribute(ADDITIONALVALUE_ATR)!=null){//handles amic, aldehydic, anilic and amoyl suffixes properly
				if (suffixInstructions.length != 2){
					throw new ComponentGenerationException("suffix: " + suffix.getValue() + " used on an inappropriate group");
				}
				symmetricSuffixes = false;
				String suffixValue = suffix.getValue();
				if (suffixValue.equals("imide")|| suffixValue.equals("imido") || suffixValue.equals("imidyl")|| suffixValue.equals("imidium")  || suffixValue.equals("imidylium")){
					imideSpecialCase =true;//prematurely resolve the two suffixes and explicitly join them to form a cyclic imide
				}
			}

			int firstIdInFragment=suffixableFragment.getIdOfFirstAtom();
			if (suffix.getAttribute(LOCANT_ATR)==null){
				suffix.addAttribute(new Attribute(LOCANTID_ATR, Integer.toString(firstIdInFragment + Integer.parseInt(suffixInstructions[0]) -1)));
			}
			for (int i = 1; i < suffixInstructions.length; i++) {
				Element newSuffix = new Element(SUFFIX_EL);
				if (symmetricSuffixes){
					newSuffix.addAttribute(new Attribute(VALUE_ATR, suffix.getAttributeValue(VALUE_ATR)));
					newSuffix.addAttribute(new Attribute(TYPE_ATR,  suffix.getAttributeValue(TYPE_ATR)));
					if (suffix.getAttribute(SUBTYPE_ATR)!=null){
						newSuffix.addAttribute(new Attribute(SUBTYPE_ATR,  suffix.getAttributeValue(SUBTYPE_ATR)));
					}
					if (suffix.getAttribute(INFIX_ATR)!=null && suffix.getAttributeValue(INFIX_ATR).startsWith("=")){//clone infixes that effect double bonds but not single bonds e.g. maleamidate still should have one functional atom
						newSuffix.addAttribute(new Attribute(INFIX_ATR,  suffix.getAttributeValue(INFIX_ATR)));
					}
				}
				else{
					newSuffix.addAttribute(new Attribute(VALUE_ATR, suffix.getAttributeValue(ADDITIONALVALUE_ATR)));
					newSuffix.addAttribute(new Attribute(TYPE_ATR, ROOT_EL));
				}
				newSuffix.addAttribute(new Attribute(LOCANTID_ATR, Integer.toString(firstIdInFragment + Integer.parseInt(suffixInstructions[i]) -1)));
				XOMTools.insertAfter(suffix, newSuffix);
				suffixes.add(newSuffix);
			}
		}
		return imideSpecialCase;
	}


	/**Processes a suffix and returns any fragment the suffix intends to add to the molecule
	 * @param state
	 * @param suffixes The suffix elements for a fragment.
	 * @param frag The fragment to which the suffix will be applied
	 * @return An arrayList containing the generated fragments
	 * @throws StructureBuildingException If the suffixes can't be resolved properly.
	 * @throws ComponentGenerationException
	 */
	private List<Fragment> resolveGroupAddingSuffixes(BuildState state, List<Element> suffixes, Fragment frag) throws StructureBuildingException, ComponentGenerationException {
		List<Fragment> suffixFragments =new ArrayList<Fragment>();
		String groupType = frag.getType();
		String subgroupType = frag.getSubType();

		String suffixTypeToUse =null;
		if (suffixApplicability.containsKey(groupType)){
			suffixTypeToUse =groupType;
		}
		else{
			suffixTypeToUse = STANDARDGROUP_TYPE_VAL;
		}

        for (Element suffix : suffixes) {
            String suffixValue = suffix.getAttributeValue(VALUE_ATR);

            boolean cyclic;//needed for addSuffixPrefixIfNonePresentAndCyclic rule
            Atom atomLikelyToBeUsedBySuffix = null;
            if (suffix.getAttribute(LOCANT_ATR) != null) {
            	atomLikelyToBeUsedBySuffix = frag.getAtomByLocant(suffix.getAttributeValue(LOCANT_ATR));
            }
            else if (suffix.getAttribute(LOCANTID_ATR) != null) {
            	atomLikelyToBeUsedBySuffix = frag.getAtomByIDOrThrow(Integer.parseInt(suffix.getAttributeValue(LOCANTID_ATR)));
            }
            if (atomLikelyToBeUsedBySuffix==null){
            	//a locant has not been specified
            	//also can happen in the cases of things like fused rings where the final numbering is not available so lookup by locant fails (in which case all the atoms will be cyclic anyway)
            	atomLikelyToBeUsedBySuffix = frag.getFirstAtom();
            }
            cyclic = atomLikelyToBeUsedBySuffix.getAtomIsInACycle();

            Elements suffixRuleTags = getSuffixRuleTags(suffixTypeToUse, suffixValue, subgroupType);
            Fragment suffixFrag = null;
            /*
             * Temp fragments are build for each addGroup rule and then merged into suffixFrag
             */
            for (int j = 0; j < suffixRuleTags.size(); j++) {
                Element suffixRuleTag = suffixRuleTags.get(j);
                String suffixRuleTagName = suffixRuleTag.getLocalName();
                if (suffixRuleTagName.equals(SUFFIXRULES_ADDGROUP_EL)) {
                    String labels = NONE_LABELS_VAL;
                    if (suffixRuleTag.getAttribute(SUFFIXRULES_LABELS_ATR) != null) {
                        labels = suffixRuleTag.getAttributeValue(SUFFIXRULES_LABELS_ATR);
                    }
                    suffixFrag = state.fragManager.buildSMILES(suffixRuleTag.getAttributeValue(SUFFIXRULES_SMILES_ATR), SUFFIX_TYPE_VAL, SUFFIX_SUBTYPE_VAL, labels);
                    List<Atom> atomList = suffixFrag.getAtomList();
                    if (suffixRuleTag.getAttribute(SUFFIXRULES_FUNCTIONALIDS_ATR) != null) {
                        String[] relativeIdsOfFunctionalAtoms = matchComma.split(suffixRuleTag.getAttributeValue(SUFFIXRULES_FUNCTIONALIDS_ATR));
                        for (String relativeId : relativeIdsOfFunctionalAtoms) {
                        	int atomIndice = Integer.parseInt(relativeId) -1;
                        	if (atomIndice >=atomList.size()){
                        		throw new StructureBuildingException("Check suffixRules.xml: Atom requested to have a functionalAtom was not within the suffix fragment");
                        	}
                        	suffixFrag.addFunctionalAtom(atomList.get(atomIndice));
						}
                    }
                    if (suffixRuleTag.getAttribute(SUFFIXRULES_OUTIDS_ATR) != null) {
                        String[] relativeIdsOfOutAtoms = matchComma.split(suffixRuleTag.getAttributeValue(SUFFIXRULES_OUTIDS_ATR));
                        for (String relativeId : relativeIdsOfOutAtoms) {
                        	int atomIndice = Integer.parseInt(relativeId) -1;
                        	if (atomIndice >=atomList.size()){
                        		throw new StructureBuildingException("Check suffixRules.xml: Atom requested to have a outAtom was not within the suffix fragment");
                        	}
                        	suffixFrag.addOutAtom(atomList.get(atomIndice), 1 , true);
						}
                    }
                }
                else if (suffixRuleTagName.equals(SUFFIXRULES_ADDSUFFIXPREFIXIFNONEPRESENTANDCYCLIC_EL)){
                	if (cyclic && suffix.getAttribute(SUFFIXPREFIX_ATR)==null){
                		suffix.addAttribute(new Attribute(SUFFIXPREFIX_ATR, suffixRuleTag.getAttributeValue(SUFFIXRULES_SMILES_ATR)));
                	}
                }
				else if (suffixRuleTagName.equals(SUFFIXRULES_ADDFUNCTIONALATOMSTOHYDROXYGROUPS_EL)){
					if (suffixFrag != null){
						throw new ComponentGenerationException("addFunctionalAtomsToHydroxyGroups is not currently compatable with the addGroup suffix rule");
					}
					addFunctionalAtomsToHydroxyGroups(atomLikelyToBeUsedBySuffix);
				}
				else if (suffixRuleTagName.equals(SUFFIXRULES_CHARGEHYDROXYGROUPS_EL)){
					if (suffixFrag != null){
						throw new ComponentGenerationException("chargeHydroxyGroups is not currently compatable with the addGroup suffix rule");
					}
					chargeHydroxyGroups(atomLikelyToBeUsedBySuffix);
					
				}
				else if (suffixRuleTagName.equals(SUFFIXRULES_REMOVEONEDOUBLEBONDEDOXYGEN_EL)){
					if (suffixFrag != null){
						throw new ComponentGenerationException("removeOneDoubleBondedOxygen is not currently compatable with the addGroup suffix rule");
					}
					removeOneDoubleBondedOxygen(state, atomLikelyToBeUsedBySuffix);
				}
            }
            if (suffixFrag != null) {
				suffixFragments.add(suffixFrag);
				state.xmlFragmentMap.put(suffix, suffixFrag);
            }
        }
		return suffixFragments;
	}
	
	/**Processes any convertHydroxyGroupsToOutAtoms instructions
	 * This is not handled as part of resolveGroupAddingSuffixes as something like carbonochloridoyl involves infix replacement
	 * on a hydroxy that would otherwise actually be removed by this rule!
	 * @param state
	 * @param suffixes The suffix elements for a fragment.
	 * @param frag The fragment to which the suffix will be applied
	 * @return An arrayList containing the generated fragments
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException 
	 */
	private void processConvertHydroxyGroupsToOutAtomsRule(BuildState state, List<Element> suffixes, Fragment frag) throws ComponentGenerationException, StructureBuildingException{
		String groupType = frag.getType();
		String subgroupType = frag.getSubType();
		String suffixTypeToUse =null;
		if (suffixApplicability.containsKey(groupType)){
			suffixTypeToUse =groupType;
		}
		else{
			suffixTypeToUse = STANDARDGROUP_TYPE_VAL;
		}
        for (Element suffix : suffixes) {
            String suffixValue = suffix.getAttributeValue(VALUE_ATR);
            Elements suffixRuleTags = getSuffixRuleTags(suffixTypeToUse, suffixValue, subgroupType);
            for (int j = 0; j < suffixRuleTags.size(); j++) {
                Element suffixRuleTag = suffixRuleTags.get(j);
                String suffixRuleTagName = suffixRuleTag.getLocalName();
                if (suffixRuleTagName.equals(SUFFIXRULES_CONVERTHYDROXYGROUPSTOOUTATOMS_EL)){
					convertHydroxyGroupsToOutAtoms(state, frag);
				}
            }
        }
	}


	/**
	 * Finds all hydroxy groups connected to a given atom and adds a functionalAtom to each of them
	 * @param atom
	 * @throws StructureBuildingException
	 */
	private void addFunctionalAtomsToHydroxyGroups(Atom atom) throws StructureBuildingException {
		List<Atom> neighbours = atom.getAtomNeighbours();
		for (Atom neighbour : neighbours) {
			if (neighbour.getElement().equals("O") && neighbour.getCharge()==0 && neighbour.getAtomNeighbours().size()==1 && atom.getFrag().findBondOrThrow(atom, neighbour).getOrder()==1){
				neighbour.getFrag().addFunctionalAtom(neighbour);
			}
		}
	}

	/**
	 * Finds all hydroxy groups connected to a given atom and makes them negatively charged
	 * @param atom
	 * @throws StructureBuildingException
	 */
	private void chargeHydroxyGroups(Atom atom) throws StructureBuildingException {
		List<Atom> neighbours = atom.getAtomNeighbours();
		for (Atom neighbour : neighbours) {
			if (neighbour.getElement().equals("O") && neighbour.getCharge()==0 && neighbour.getAtomNeighbours().size()==1 && atom.getFrag().findBondOrThrow(atom, neighbour).getOrder()==1){
				neighbour.setCharge(-1);
			}
		}
	}

	/**
	 * Removes a double bonded Oxygen from the atom (an [N+][O-] is treated as N=O)
	 * An exception is thrown if no double bonded oxygen could be found connected to the atom
	 * @param state
	 * @param atom
	 * @throws StructureBuildingException
	 */
	private void removeOneDoubleBondedOxygen(BuildState state, Atom atom) throws StructureBuildingException {
		//TODO prioritise [N+][O-]
		List<Atom> neighbours = atom.getAtomNeighbours();
		for (Atom neighbour : neighbours) {
			if (neighbour.getElement().equals("O") && neighbour.getAtomNeighbours().size()==1){
				Bond b = atom.getFrag().findBondOrThrow(atom, neighbour);
				if (b.getOrder()==2 && neighbour.getCharge()==0){
					state.fragManager.removeAtomAndAssociatedBonds(neighbour);
					if (atom.getLambdaConventionValency()!=null){//corrects valency for phosphin/arsin/stibin
						atom.setLambdaConventionValency(atom.getLambdaConventionValency()-2);
					}
					if (atom.getMinimumValency()!=null){//corrects valency for phosphin/arsin/stibin
						atom.setMinimumValency(atom.getMinimumValency()-2);
					}
					return;
				}
				else if (neighbour.getCharge() ==-1 && b.getOrder()==1){
					if (atom.getCharge() ==1 && atom.getElement().equals("N")){
						state.fragManager.removeAtomAndAssociatedBonds(neighbour);
						atom.setCharge(0);
						return;
					}
				}
			}
		}
		throw new StructureBuildingException("Double bonded oxygen not found in fragment. Perhaps a suffix has been used inappropriately");
	}
	
	/**
	 * 
	 * @param state
	 * @param frag
	 * @throws StructureBuildingException
	 */
	private void convertHydroxyGroupsToOutAtoms(BuildState state, Fragment frag) throws StructureBuildingException {
		List<Atom> atomList = frag.getAtomList();
		for (Atom atom : atomList) {
			if (atom.getElement().equals("O") && atom.getCharge()==0){
				List<Atom> neighbours = atom.getAtomNeighbours();
				if (neighbours.size()==1 && frag.findBondOrThrow(atom, neighbours.get(0)).getOrder()==1){
					state.fragManager.removeAtomAndAssociatedBonds(atom);
					frag.addOutAtom(neighbours.get(0), 1, true);
				}
			}
		}
	}

	/**
	 * Returns the appropriate suffixRule tags for the given arguements.
	 * The suffix rule tags are the children of the appropriate rule in suffixRules.xml
	 * @param suffixTypeToUse
	 * @param suffixValue
	 * @param subgroupType
	 * @return
	 * @throws ComponentGenerationException
	 */
	private Elements getSuffixRuleTags(String suffixTypeToUse, String suffixValue, String subgroupType) throws ComponentGenerationException {
		HashMap<String, List<Element>> groupToSuffixMap = suffixApplicability.get(suffixTypeToUse);
		if (groupToSuffixMap==null){
			throw new ComponentGenerationException("Suffix Type: "+ suffixTypeToUse + " does not have a corresponding groupType entry in suffixApplicability.xml");
		}
		List<Element> potentiallyApplicableSuffixes =groupToSuffixMap.get(suffixValue);
		if(potentiallyApplicableSuffixes==null || potentiallyApplicableSuffixes.size()==0 ) {
			throw new ComponentGenerationException("Suffix: " +suffixValue +" does not apply to the group it was associated with (type: "+  suffixTypeToUse + ")according to suffixApplicability.xml");
		}
		Element chosenSuffix=null;
        for (Element suffix : potentiallyApplicableSuffixes) {
            if (suffix.getAttribute(SUFFIXAPPLICABILITY_SUBTYPE_ATR) != null) {
                if (!suffix.getAttributeValue(SUFFIXAPPLICABILITY_SUBTYPE_ATR).equals(subgroupType)) {
                    continue;
                }
            }
            if (chosenSuffix != null) {
                throw new ComponentGenerationException("Suffix: " + suffixValue + " appears multiple times in suffixApplicability.xml");
            }
            chosenSuffix = suffix;
        }
		if (chosenSuffix==null){
			throw new ComponentGenerationException("Suffix: " +suffixValue +" does not apply to the group it was associated with (type: "+  suffixTypeToUse + ")due to the group's subType: "+ subgroupType +" according to suffixApplicability.xml");
		}
		Element rule =suffixRules.get(chosenSuffix.getValue());
		if(rule ==null) {
			throw new ComponentGenerationException("Suffix: " +chosenSuffix.getValue() +" does not have a rule associated with it in suffixRules.xml");
		}
		return rule.getChildElements();
	}


	/**
	 * Searches for suffix elements with the suffixPrefix attribute set
	 * A suffixPrefix is something like sulfon in sulfonamide. It would in this case take the value S(=O)
	 * @param state
	 * @param suffixes
	 * @throws StructureBuildingException
	 */
	private void processSuffixPrefixes(BuildState state, List<Element> suffixes) throws StructureBuildingException{
		for (Element suffix : suffixes) {
			if (suffix.getAttribute(SUFFIXPREFIX_ATR)!=null){
				Fragment suffixPrefixFrag = state.fragManager.buildSMILES(suffix.getAttributeValue(SUFFIXPREFIX_ATR), SUFFIX_TYPE_VAL, NONE_LABELS_VAL);
				addFunctionalAtomsToHydroxyGroups(suffixPrefixFrag.getFirstAtom());
				if (suffix.getValue().endsWith("ate")){
					chargeHydroxyGroups(suffixPrefixFrag.getFirstAtom());
				}
				Atom firstAtomOfPrefix = suffixPrefixFrag.getFirstAtom();
				firstAtomOfPrefix.addLocant("X");//make sure this atom is not given a locant
				Fragment suffixFrag = state.xmlFragmentMap.get(suffix);
				state.fragManager.incorporateFragment(suffixPrefixFrag, suffixFrag);
				
				//manipulate suffixFrag such that all the bonds to the first atom (the R)  go instead to the first atom of suffixPrefixFrag.
				//Then reconnect the R to that atom
				Atom theR = suffixFrag.getFirstAtom();
				List<Atom> neighbours = theR.getAtomNeighbours();
				for (Atom neighbour : neighbours) {
					Bond b = suffixFrag.findBondOrThrow(theR, neighbour);
					state.fragManager.removeBond(b);
					state.fragManager.createBond(neighbour, firstAtomOfPrefix, b.getOrder());
				}
				state.fragManager.createBond(firstAtomOfPrefix, theR, 1);
			}
		}
	}

	/**
	 * Checks through the groups accesible from the startingElement taking into account brackets (i.e. those that it is feasible that the group of the startingElement could substitute onto).
	 * It is assumed that one does not intentionally locant onto something in a deeper level of bracketting (not implicit bracketing). e.g. 2-propyl(ethyl)ammonia will give prop-2-yl
	 * @param state
	 * @param startingElement
	 * @param locant: the locant string to check for the presence of
	 * @return whether the locant was found
	 * @throws StructureBuildingException
	 */
	private boolean checkLocantPresentOnPotentialRoot(BuildState state, Element startingElement, String locant) throws StructureBuildingException {
		boolean foundSibling =false;
		Stack<Element> s = new Stack<Element>();
		s.add(startingElement);
		boolean doneFirstIteration =false;//check on index only done on first iteration to only get elements with an index greater than the starting element
		while (s.size()>0){
			Element currentElement =s.pop();
			Element parent = (Element)currentElement.getParent();
			List<Element> siblings = XOMTools.getChildElementsWithTagNames(parent, new String[]{BRACKET_EL, SUBSTITUENT_EL, ROOT_EL});
			int indexOfCurrentElement =parent.indexOf(currentElement);

			for (Element bracketOrSub : siblings) {
				if (!doneFirstIteration && parent.indexOf(bracketOrSub) <= indexOfCurrentElement){
					continue;
				}
				if (bracketOrSub.getLocalName().equals(BRACKET_EL)){//only want to consider implicit brackets, not proper brackets
					if (bracketOrSub.getAttribute(TYPE_ATR)==null){
						continue;
					}
					s.push((Element)bracketOrSub.getChild(0));
				}
				else{
					Element group = bracketOrSub.getFirstChildElement(GROUP_EL);
					Fragment groupFrag =state.xmlFragmentMap.get(group);
					if (groupFrag.hasLocant(locant)){
						return true;
					}
					List<Fragment> suffixes =state.xmlSuffixMap.get(group);
					if (suffixes!=null){
						for (Fragment suffix : suffixes) {
							if (suffix.hasLocant(locant)){
								return true;
							}
						}
					}
				}
				foundSibling =true;
			}
			doneFirstIteration =true;
		}

		if (!foundSibling){//Special case: anything the group could potentially substitute onto is in a bracket. The bracket is checked recursively
			s = new Stack<Element>();
			s.add(startingElement);
			doneFirstIteration =false;//check on index only done on first iteration to only get elements with an index greater than the starting element
			while (s.size()>0){
				Element currentElement =s.pop();
				Element parent = (Element)currentElement.getParent();
				List<Element> siblings = XOMTools.getChildElementsWithTagNames(parent, new String[]{"bracket", "substituent", "root"});
				int indexOfCurrentElement =parent.indexOf(currentElement);

				for (Element bracketOrSub : siblings) {
					if (!doneFirstIteration && parent.indexOf(bracketOrSub) <= indexOfCurrentElement){
						continue;
					}
					if (bracketOrSub.getLocalName().equals("bracket")){
						s.push((Element)bracketOrSub.getChild(0));
					}
					else{
						Element group = bracketOrSub.getFirstChildElement(GROUP_EL);
						Fragment groupFrag =state.xmlFragmentMap.get(group);
						if (groupFrag.hasLocant(locant)){
							return true;
						}
						List<Fragment> suffixes =state.xmlSuffixMap.get(group);
						if (suffixes!=null){
							for (Fragment suffix : suffixes) {
								if (suffix.hasLocant(locant)){
									return true;
								}
							}
						}
					}
				}
				doneFirstIteration =true;
			}
		}

		return false;
	}

	/**
	 * Handles Hantzsch-Widman rings. Adds SMILES to the group corresponding to the ring's structure
	 * @param state
	 * @param subOrRoot
	 * @throws StructureBuildingException
	 * @throws ComponentGenerationException
	 */
	private void processHW(BuildState state, Element subOrRoot) throws StructureBuildingException, ComponentGenerationException{
		List<Element> hwGroups = XOMTools.getChildElementsWithTagNameAndAttribute(subOrRoot, GROUP_EL, SUBTYPE_ATR, HANTZSCHWIDMAN_SUBTYPE_VAL);
		for (Element group : hwGroups) {
			Fragment hwRing =state.xmlFragmentMap.get(group);
			List<Atom> atomList =hwRing.getAtomList();
			Element prev = (Element) XOMTools.getPreviousSibling(group);
			ArrayList<Element> prevs = new ArrayList<Element>();
			boolean noLocants = true;
			while(prev != null && prev.getLocalName().equals(HETEROATOM_EL)) {
				prevs.add(prev);
				if(prev.getAttribute(LOCANT_ATR) != null) {
					noLocants = false;
				}
				prev = (Element) XOMTools.getPreviousSibling(prev);
			}
			if (atomList.size() == 6 && group.getValue().equals("an")){
				boolean hasNitrogen = false;
				boolean hasSiorGeorSnorPb=false;
				boolean saturatedRing =true;
				for(Element heteroatom : prevs){
					String heteroAtomElement =heteroatom.getAttributeValue(VALUE_ATR);
					Matcher m = matchElementSymbol.matcher(heteroAtomElement);
					if (!m.find()){
						throw new ComponentGenerationException("Failed to extract element from HW heteroatom");
					}
					heteroAtomElement = m.group();
					if (heteroAtomElement.equals("N")){
						hasNitrogen=true;
					}
					if (heteroAtomElement.equals("Si") ||
						heteroAtomElement.equals("Ge") ||
						heteroAtomElement.equals("Sn") ||
						heteroAtomElement.equals("Pb") ){
						hasSiorGeorSnorPb =true;
					}
				}
				for (Atom a: atomList) {
					if (a.hasSpareValency()){
						saturatedRing =false;
					}
				}
				if (saturatedRing && !hasNitrogen && hasSiorGeorSnorPb){
					throw new ComponentGenerationException("Blocked HW system (6 member saturated ring with no nitrogen but has Si/Ge/Sn/Pb)");
				}
			}
			String name = "";
			Collections.reverse(prevs);
			for(Element heteroatom : prevs) name += heteroatom.getValue();
			name += group.getValue();
			name = name.toLowerCase();
			if(noLocants && prevs.size() > 0) {
				if(specialHWRings.containsKey(name)) {
					String[] specialRingInformation =specialHWRings.get(name);
					if (specialRingInformation[0].equals("blocked")){
						throw new ComponentGenerationException("Blocked HW system");
					}
					else if (specialRingInformation[0].equals("saturated")){
						for (Atom a: hwRing.getAtomList()) {
							a.setSpareValency(false);
						}
					}//something like oxazole where by convention locants go 1,3 or a inorganic HW-like system
					for (int j = 1; j < specialRingInformation.length; j++) {
						Atom a =hwRing.getAtomByLocantOrThrow(Integer.toString(j));
						a.setElement(specialRingInformation[j]);
					}
					for(Element p : prevs){
						p.detach();
					}
					prevs.clear();
				}
			}
			HashSet<Element> elementsToRemove =new HashSet<Element>();
			for(Element heteroatom : prevs){//add locanted heteroatoms
				if (heteroatom.getAttribute(LOCANT_ATR) !=null){
					String locant =heteroatom.getAttributeValue(LOCANT_ATR);
					String elementReplacement =heteroatom.getAttributeValue(VALUE_ATR);
					Matcher m = matchElementSymbol.matcher(elementReplacement);
					if (!m.find()){
						throw new ComponentGenerationException("Failed to extract element from HW heteroatom");
					}
					elementReplacement = m.group();
					Atom a =hwRing.getAtomByLocantOrThrow(locant);
					a.setElement(elementReplacement);
					if (heteroatom.getAttribute(LAMBDA_ATR)!=null){
						a.setLambdaConventionValency(Integer.parseInt(heteroatom.getAttributeValue(LAMBDA_ATR)));
					}
					heteroatom.detach();
					elementsToRemove.add(heteroatom);
				}
			}
			for(Element p : elementsToRemove){
				prevs.remove(p);
			}

			//add unlocanted heteroatoms
			int defaultLocant=1;
			for(Element heteroatom : prevs){
				String elementReplacement =heteroatom.getAttributeValue(VALUE_ATR);
				Matcher m = matchElementSymbol.matcher(elementReplacement);
				if (!m.find()){
					throw new ComponentGenerationException("Failed to extract element from HW heteroatom");
				}
				elementReplacement = m.group();

				while (!hwRing.getAtomByLocantOrThrow(Integer.toString(defaultLocant)).getElement().equals("C")){
					defaultLocant++;
				}
				Atom a =hwRing.getAtomByLocantOrThrow(Integer.toString(defaultLocant));
				a.setElement(elementReplacement);
				if (heteroatom.getAttribute(LAMBDA_ATR)!=null){
					a.setLambdaConventionValency(Integer.parseInt(heteroatom.getAttributeValue(LAMBDA_ATR)));
				}
				heteroatom.detach();
			}

			Elements deltas = subOrRoot.getChildElements(DELTA_EL);//add specified double bonds
			for (int j = 0; j < deltas.size(); j++) {
				String locantOfDoubleBond = deltas.get(j).getValue();
				Atom firstInDoubleBond;
				Atom secondInDoubleBond;
				if (locantOfDoubleBond.equals("")){
					int defaultId=hwRing.getIdOfFirstAtom();
					firstInDoubleBond =hwRing.getAtomByIDOrThrow(defaultId);
					secondInDoubleBond =hwRing.getAtomByIDOrThrow(defaultId +1);
					while (firstInDoubleBond.hasSpareValency() || !ValencyChecker.checkValencyAvailableForBond(firstInDoubleBond, 1) ||
							secondInDoubleBond.hasSpareValency() || !ValencyChecker.checkValencyAvailableForBond(secondInDoubleBond, 1)){
						defaultId++;
						firstInDoubleBond =hwRing.getAtomByIDOrThrow(defaultId);
						secondInDoubleBond =hwRing.getAtomByIDOrThrow(defaultId +1);
						if (firstInDoubleBond.getType().equals(SUFFIX_TYPE_VAL) || secondInDoubleBond.getType().equals(SUFFIX_TYPE_VAL)){
							throw new StructureBuildingException("No suitable atom found");
						}
					}
				}
				else{
					firstInDoubleBond = hwRing.getAtomByLocantOrThrow(locantOfDoubleBond);
					secondInDoubleBond = hwRing.getAtomByIDOrThrow(firstInDoubleBond.getID() +1);
				}
				Bond b =hwRing.findBond(firstInDoubleBond, secondInDoubleBond);
				b.setOrder(2);
				deltas.get(j).detach();
			}
			XOMTools.setTextChild(group, name);
		}
	}


	/**
	 * Assigns Element symbols to groups, suffixes and conjunctive suffixes.
	 * Suffixes have preference.
	 * @param state
	 * @param subOrRoot
	 * @throws StructureBuildingException 
	 */
	private void assignElementSymbolLocants(BuildState state, Element subOrRoot) throws StructureBuildingException {
		List<Element> groups = XOMTools.getChildElementsWithTagName(subOrRoot, GROUP_EL);
		Element lastGroupElementInSubOrRoot =groups.get(groups.size()-1);
		List<Fragment> suffixFragments = new ArrayList<Fragment>(state.xmlSuffixMap.get(lastGroupElementInSubOrRoot));
		Fragment suffixableFragment =state.xmlFragmentMap.get(lastGroupElementInSubOrRoot);
		//treat conjunctive suffixesas if they were suffixes
		List<Element> conjunctiveGroups = XOMTools.getChildElementsWithTagName(subOrRoot, CONJUNCTIVESUFFIXGROUP_EL);
		for (Element group : conjunctiveGroups) {
			suffixFragments.add(state.xmlFragmentMap.get(group));
		}
		FragmentTools.assignElementLocants(suffixableFragment, suffixFragments);
		for (int i = groups.size()-2; i>=0; i--) {
			FragmentTools.assignElementLocants(state.xmlFragmentMap.get(groups.get(i)), new ArrayList<Fragment>());
		}
	}


	/**
	 * Processes constructs such as biphenyl, 1,1':4',1''-Terphenyl, 2,2'-Bipyridylium, m-Quaterphenyl
	 * @param state
	 * @param subOrRoot
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void processRingAssemblies(BuildState state, Element subOrRoot) throws ComponentGenerationException, StructureBuildingException {
		List<Element> ringAssemblyMultipliers = XOMTools.getChildElementsWithTagName(subOrRoot, RINGASSEMBLYMULTIPLIER_EL);
		for (Element multiplier : ringAssemblyMultipliers) {
			int mvalue = Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR));

			/*
			 * Populate locants with locants. Two locants are required for every pair of rings to be joined.
			 * e.g. bi requires 2, ter requires 4 etc.
			 */
			List<List<String>> ringJoiningLocants =new ArrayList<List<String>>();
			Element previousEl =(Element)XOMTools.getPreviousSibling(multiplier);
			Element group =(Element)XOMTools.getNextSibling(multiplier, GROUP_EL);
			if (previousEl!=null && previousEl.getLocalName().equals(RINGASSEMBLYLOCANT_EL)){//a locant appears to have provided to indicate how to connect the rings of the ringAssembly
				String locantText =StringTools.removeDashIfPresent(previousEl.getValue());
				//special cases where often locants are meant to apply to suffixes rather than being a description of where the rings connect to each other
				if (group.getValue().equals("phen") || group.getValue().equals("hex") || group.getValue().equals("benz")){
					//Find elements that can have locants but don't currently
					List<Element> locantAble = findElementsMissingIndirectLocants(subOrRoot, previousEl);
					if(2 <= locantAble.size()) {
						throw new ComponentGenerationException("Most likely the ringAssemblyLocant: " + previousEl.getValue() + " is actually a normal locant that is supposed to apply to elements after the ring assembly");
					}
				}
				//locantText might be something like 1,1':3',1''
				String[] perRingLocantArray =matchColon.split(locantText);
				if (perRingLocantArray.length !=(mvalue -1)){
					throw new ComponentGenerationException("Disagreement between number of locants(" + locantText +") and ring assembly multiplier: " + mvalue);
				}
				for (int j = 0; j < perRingLocantArray.length; j++) {
					String[] locantArray = matchComma.split(perRingLocantArray[j]);
					if (locantArray.length !=2){
						throw new ComponentGenerationException("missing locant, expected 2 locants: " + perRingLocantArray[j]);
					}
					ringJoiningLocants.add(Arrays.asList(locantArray));
				}
				previousEl.detach();
			}
			else if (previousEl!=null && previousEl.getLocalName().equals(LOCANT_EL)){
				if (previousEl.getAttribute(TYPE_ATR)!=null && previousEl.getAttributeValue(TYPE_ATR).equals(ORTHOMETAPARA_TYPE_VAL)){//an OMP locant appears to have provided to indicate how to connect the rings of the ringAssembly
					String locant2 =previousEl.getValue();
					String locant1 ="1";
					ArrayList<String> locantArrayList =new ArrayList<String>();
					locantArrayList.add("1");
					locantArrayList.add("1'");
					ringJoiningLocants.add(locantArrayList);
					for (int j = 1; j < mvalue -1; j++) {
						locantArrayList =new ArrayList<String>();
						locantArrayList.add(locant2 + StringTools.multiplyString("'", j));
						locantArrayList.add(locant1 + StringTools.multiplyString("'", j+1));
						ringJoiningLocants.add(locantArrayList);
					}
					previousEl.detach();
				}
			}

			Element elementToResolve = new Element(SUBSTITUENT_EL);//temporary element containing elements that should be resolved before the ring is duplicated
			Element nextEl =(Element) XOMTools.getNextSibling(multiplier);
			if (nextEl.getLocalName().equals(STRUCTURALOPENBRACKET_EL)){//brackets have been provided to aid disambiguation. These brackets are detached e.g. bi(cyclohexyl)
				Element currentEl =nextEl;
				nextEl = (Element) XOMTools.getNextSibling(currentEl);
				currentEl.detach();
				while (nextEl !=null && !nextEl.getLocalName().equals(STRUCTURALCLOSEBRACKET_EL)){
					currentEl =nextEl;
					nextEl = (Element) XOMTools.getNextSibling(currentEl);
					currentEl.detach();
					elementToResolve.appendChild(currentEl);
				}
				if (nextEl!=null){
					nextEl.detach();
				}
			}
			else{
				boolean groupFound = false;
				boolean inlineSuffixSeen = false;
				while (nextEl !=null){
					Element currentEl =nextEl;
					nextEl = (Element) XOMTools.getNextSibling(currentEl);
					if (!groupFound ||
							(!inlineSuffixSeen && currentEl.getLocalName().equals(SUFFIX_EL) && currentEl.getAttributeValue(TYPE_ATR).equals(INLINE_TYPE_VAL) && currentEl.getAttribute(LOCANT_ATR)==null)||
							(currentEl.getLocalName().equals(SUFFIX_EL) && currentEl.getAttributeValue(TYPE_ATR).equals(CHARGE_TYPE_VAL))){
						currentEl.detach();
						elementToResolve.appendChild(currentEl);
					}
					else{
						break;
					}
					if (currentEl.getLocalName().equals(GROUP_EL)){
						groupFound = true;
					}
					if ((currentEl.getLocalName().equals(SUFFIX_EL) && currentEl.getAttributeValue(TYPE_ATR).equals(INLINE_TYPE_VAL))){
						inlineSuffixSeen = true;
					}
				}
			}

			List<Element> suffixes = XOMTools.getChildElementsWithTagName(elementToResolve, SUFFIX_EL);
			Fragment fragmentToResolveAndDuplicate =state.xmlFragmentMap.get(group);
			resolveSuffixes(state, group, suffixes);
			StructureBuildingMethods.resolveLocantedFeatures(state, elementToResolve);
			StructureBuildingMethods.resolveUnLocantedFeatures(state, elementToResolve);
			group.detach();
			XOMTools.insertAfter(multiplier, group);

			int bondOrder = 1;
			if (fragmentToResolveAndDuplicate.getOutAtoms().size()>0){//e.g. bicyclohexanylidene
				bondOrder =fragmentToResolveAndDuplicate.getOutAtom(0).getValency();
				fragmentToResolveAndDuplicate.removeOutAtom(0);
			}
			if (fragmentToResolveAndDuplicate.getOutAtoms().size()>0){
				throw new StructureBuildingException("Ring assembly fragment should have one or no OutAtoms; not more than one!");
			}

			List<Fragment> clonedFragments = new ArrayList<Fragment>();
			for (int j = 1; j < mvalue; j++) {
				clonedFragments.add(state.fragManager.copyAndRelabelFragment(fragmentToResolveAndDuplicate, StringTools.multiplyString("'", j)));
			}
			for (int j = 0; j < mvalue-1; j++) {
				Fragment clone =clonedFragments.get(j);
				Atom atomOnParent;
				Atom atomOnLatestClone;
				if (ringJoiningLocants.size()>0){//locants defined
					atomOnParent = fragmentToResolveAndDuplicate.getAtomByLocantOrThrow(ringJoiningLocants.get(j).get(0));
					atomOnLatestClone = clone.getAtomByLocantOrThrow(ringJoiningLocants.get(j).get(1));
				}
				else{
					atomOnParent =fragmentToResolveAndDuplicate.getAtomOrNextSuitableAtomOrThrow(fragmentToResolveAndDuplicate.getDefaultInAtom(), bondOrder, true);
					atomOnLatestClone = clone.getAtomOrNextSuitableAtomOrThrow(clone.getDefaultInAtom(), bondOrder, true);
				}
				state.fragManager.incorporateFragment(clone, atomOnLatestClone.getID(), fragmentToResolveAndDuplicate, atomOnParent.getID(), bondOrder);
			}
			XOMTools.setTextChild(group, multiplier.getValue() +group.getValue());
			Element possibleOpenStructuralBracket = (Element) XOMTools.getPreviousSibling(multiplier);
			if (possibleOpenStructuralBracket!=null && possibleOpenStructuralBracket.getLocalName().equals(STRUCTURALOPENBRACKET_EL)){//e.g. [2,2'-bipyridin].
				//To emphasise there can actually be two sets of structural brackets e.g. [1,1'-bi(cyclohexyl)]
				XOMTools.getNextSibling(possibleOpenStructuralBracket, STRUCTURALCLOSEBRACKET_EL).detach();
				possibleOpenStructuralBracket.detach();
			}
			multiplier.detach();
		}
	}


	/**
	 * Proccess any polycyclic spiro systems present in subOrRoot
	 * It is assumed that at this stage all hantzch widman rings/fused rings have been resolved to single groups allowing them to be simply spiro fused
	 * 
	 * http://www.chem.qmul.ac.uk/iupac/spiro/ (SP-2 through SP-6)
	 * @param state
	 * @param subOrRoot
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void processPolyCyclicSpiroNomenclature(BuildState state, Element subOrRoot) throws ComponentGenerationException, StructureBuildingException {
		List<Element> polyCyclicSpiros = XOMTools.getChildElementsWithTagName(subOrRoot, POLYCYCLICSPIRO_EL);
		if (polyCyclicSpiros.size()>0){
			if (polyCyclicSpiros.size()!=1){
				throw new ComponentGenerationException("Nested polyspiro systems are not supported");
			}
			Element polyCyclicSpiroDescriptor = polyCyclicSpiros.get(0);
			String value = polyCyclicSpiroDescriptor.getAttributeValue(VALUE_ATR);
			if (value.equals("spiro")){
				processNonIdenticalPolyCyclicSpiro(state, polyCyclicSpiroDescriptor);
			}
			else if (value.equals("spirobi")){
				processSpiroBiOrTer(state, polyCyclicSpiroDescriptor, 2);
			}
			else if (value.equals("spiroter")){
				processSpiroBiOrTer(state, polyCyclicSpiroDescriptor, 3);
			}
			else if (value.equals("dispiroter")){
				processDispiroter(state, polyCyclicSpiroDescriptor);
			}
			else{
				throw new ComponentGenerationException("Only identical components supported so far");
			}
			polyCyclicSpiroDescriptor.detach();
		}
	}

	private void processNonIdenticalPolyCyclicSpiro(BuildState state, Element polyCyclicSpiroDescriptor) throws ComponentGenerationException, StructureBuildingException {
		Element subOrRoot = (Element) polyCyclicSpiroDescriptor.getParent();
		List<Element> groups = XOMTools.getChildElementsWithTagName(subOrRoot, GROUP_EL);
		if (groups.size()<2){
			throw new ComponentGenerationException("OPSIN Bug: Atleast two groups were expected in polycyclic spiro system");
		}
		Element openBracket = (Element) XOMTools.getNextSibling(polyCyclicSpiroDescriptor);
		if (!openBracket.getLocalName().equals(STRUCTURALOPENBRACKET_EL)){
			throw new ComponentGenerationException("OPSIN Bug: Open bracket not found where open bracket expeced");
		}
		List<Element> spiroBracketElements = XOMTools.getSiblingsUpToElementWithTagName(openBracket, STRUCTURALCLOSEBRACKET_EL);
		Element closeBracket = (Element) XOMTools.getNextSibling(spiroBracketElements.get(spiroBracketElements.size()-1));
		if (closeBracket == null || !closeBracket.getLocalName().equals(STRUCTURALCLOSEBRACKET_EL)){
			throw new ComponentGenerationException("OPSIN Bug: Open bracket not found where open bracket expeced");
		}
		
		Element firstGroup = groups.get(0);
		List<Element> firstGroupEls = new ArrayList<Element>();
		int indexOfOpenBracket = subOrRoot.indexOf(openBracket);
		int indexOfFirstGroup = subOrRoot.indexOf(firstGroup);
		for (int i =indexOfOpenBracket +1; i < indexOfFirstGroup; i++) {
			firstGroupEls.add((Element) subOrRoot.getChild(i));
		}
		firstGroupEls.add(firstGroup);
		firstGroupEls.addAll(XOMTools.getNextAdjacentSiblingsOfType(firstGroup, UNSATURATOR_EL));
		resolveFeaturesOntoGroup(state, firstGroupEls);
		Set<Atom> spiroAtoms = new HashSet<Atom>();
		for (int i = 1; i < groups.size(); i++) {
			Element nextGroup =groups.get(i);
			Element locant = (Element) XOMTools.getNextSibling(groups.get(i-1), SPIROLOCANT_EL);
			if (locant ==null){
				throw new ComponentGenerationException("Unable to find locantEl for polycyclic spiro system");
			}
			
			List<Element> nextGroupEls = new ArrayList<Element>();
			int indexOfLocant = subOrRoot.indexOf(locant);
			int indexOfNextGroup = subOrRoot.indexOf(nextGroup);
			for (int j =indexOfLocant +1; j < indexOfNextGroup; j++) {
				nextGroupEls.add((Element) subOrRoot.getChild(j));
			}
			nextGroupEls.add(nextGroup);
			nextGroupEls.addAll(XOMTools.getNextAdjacentSiblingsOfType(nextGroup, UNSATURATOR_EL));
			resolveFeaturesOntoGroup(state, nextGroupEls);
			
			String[] locants = matchComma.split(StringTools.removeDashIfPresent(locant.getValue()));
			if (locants.length!=2){
				throw new ComponentGenerationException("Incorrect number of locants found before component of polycyclic spiro system");
			}
			locant.detach();
			Fragment nextFragment = state.xmlFragmentMap.get(nextGroup);
			FragmentTools.relabelNumericLocants(nextFragment.getAtomList(), StringTools.multiplyString("'", i));
			Atom atomToBeReplaced = nextFragment.getAtomByLocantOrThrow(locants[1]);
			Atom atomOnParentFrag = null;
			for (int j = 0; j < i; j++) {
				atomOnParentFrag = state.xmlFragmentMap.get(groups.get(j)).getAtomByLocant(locants[0]);
				if (atomOnParentFrag!=null){
					break;
				}
			}
			if (atomOnParentFrag==null){
				throw new ComponentGenerationException("Could not find the atom with locant " + locants[0] +" for use in polycyclic spiro system");
			}
			spiroAtoms.add(atomOnParentFrag);
			state.fragManager.replaceAtomWithAnotherAtomPreservingConnectivity(atomToBeReplaced, atomOnParentFrag);
			if (atomToBeReplaced.hasSpareValency()){
				atomOnParentFrag.setSpareValency(true);
			}
		}
		if (spiroAtoms.size()>1){
			Element expectedMultiplier = (Element) XOMTools.getPreviousSibling(polyCyclicSpiroDescriptor);
			if (expectedMultiplier!=null && expectedMultiplier.getLocalName().equals(MULTIPLIER_EL) && Integer.parseInt(expectedMultiplier.getAttributeValue(VALUE_ATR))==spiroAtoms.size()){
				expectedMultiplier.detach();
			}
		}
		Element rootGroup = groups.get(groups.size()-1);
		Fragment rootFrag = state.xmlFragmentMap.get(rootGroup);
		String name = rootGroup.getValue();
		for (int i = 0; i < groups.size() -1; i++) {
			Element group =groups.get(i);
			state.fragManager.incorporateFragment(state.xmlFragmentMap.get(group), rootFrag);
			name = group.getValue() + name;
			group.detach();
		}
		XOMTools.setTextChild(rootGroup, polyCyclicSpiroDescriptor.getValue() + name);
		openBracket.detach();
		closeBracket.detach();
	}


	/**
	 * Two or three copies of the fragment after polyCyclicSpiroDescriptor are spiro fused at one centre
	 * @param state
	 * @param polyCyclicSpiroDescriptor
	 * @param components
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void processSpiroBiOrTer(BuildState state, Element polyCyclicSpiroDescriptor, int components) throws ComponentGenerationException, StructureBuildingException {
		Element locant = (Element) XOMTools.getPreviousSibling(polyCyclicSpiroDescriptor);
		if (locant ==null || !locant.getLocalName().equals(LOCANT_EL)){
			throw new ComponentGenerationException("Unable to find locantEl for polycyclic spiro system");
		}
		String[] locants = matchComma.split(locant.getValue());
		if (locants.length!=components){
			throw new ComponentGenerationException("Mismatch between spiro descriptor and number of locants provided");
		}
		Element group = (Element) XOMTools.getNextSibling(polyCyclicSpiroDescriptor, GROUP_EL);
		if (group==null){
			throw new ComponentGenerationException("Cannot find group to which spirobi/ter descriptor applies");
		}

		determineFeaturesToResolveInSingleComponentSpiro(state, polyCyclicSpiroDescriptor);
		Fragment fragment = state.xmlFragmentMap.get(group);
		List<Fragment> clones = new ArrayList<Fragment>();
		for (int i = 1; i < components ; i++) {
			clones.add(state.fragManager.copyAndRelabelFragment(fragment, StringTools.multiplyString("'", i)));
		}
		for (Fragment clone : clones) {
			state.fragManager.incorporateFragment(clone, fragment);
		}
		
		Atom atomOnOriginalFragment = fragment.getAtomByLocantOrThrow(locants[0]);
		for (int i = 1; i < components ; i++) {
			Atom atomToBeReplaced = fragment.getAtomByLocantOrThrow(locants[i]);
			state.fragManager.replaceAtomWithAnotherAtomPreservingConnectivity(atomToBeReplaced, atomOnOriginalFragment);
			if (atomToBeReplaced.hasSpareValency()){
				atomOnOriginalFragment.setSpareValency(true);
			}
		}
		locant.detach();
		XOMTools.setTextChild(group, polyCyclicSpiroDescriptor.getValue() + group.getValue());
	}

	/**
	 * Three copies of the fragment after polyCyclicSpiroDescriptor are spiro fused at two centres
	 * @param state
	 * @param polyCyclicSpiroDescriptor
	 * @throws StructureBuildingException
	 * @throws ComponentGenerationException
	 */
	private void processDispiroter(BuildState state, Element polyCyclicSpiroDescriptor) throws StructureBuildingException, ComponentGenerationException {
		String value = polyCyclicSpiroDescriptor.getValue();
		value = value.substring(0, value.length()-10);//remove dispiroter
		value = StringTools.removeDashIfPresent(value);
		String[] locants = matchColon.split(value);
		Element group = (Element) XOMTools.getNextSibling(polyCyclicSpiroDescriptor, GROUP_EL);
		if (group==null){
			throw new ComponentGenerationException("Cannot find group to which dispiroter descriptor applies");
		}
		determineFeaturesToResolveInSingleComponentSpiro(state, polyCyclicSpiroDescriptor);
		Fragment fragment = state.xmlFragmentMap.get(group);
		List<Fragment> clones = new ArrayList<Fragment>();
		for (int i = 1; i < 3 ; i++) {
			clones.add(state.fragManager.copyAndRelabelFragment(fragment, StringTools.multiplyString("'", i)));
		}
		for (Fragment clone : clones) {
			state.fragManager.incorporateFragment(clone, fragment);
		}
		
		Atom atomOnLessPrimedFragment = fragment.getAtomByLocantOrThrow(matchComma.split(locants[0])[0]);
		Atom atomToBeReplaced = fragment.getAtomByLocantOrThrow(matchComma.split(locants[0])[1]);
		state.fragManager.replaceAtomWithAnotherAtomPreservingConnectivity(atomToBeReplaced, atomOnLessPrimedFragment);
		if (atomToBeReplaced.hasSpareValency()){
			atomOnLessPrimedFragment.setSpareValency(true);
		}
		
		atomOnLessPrimedFragment = fragment.getAtomByLocantOrThrow(matchComma.split(locants[1])[0]);
		atomToBeReplaced = fragment.getAtomByLocantOrThrow(matchComma.split(locants[1])[1]);
		state.fragManager.replaceAtomWithAnotherAtomPreservingConnectivity(atomToBeReplaced, atomOnLessPrimedFragment);
		if (atomToBeReplaced.hasSpareValency()){
			atomOnLessPrimedFragment.setSpareValency(true);
		}

		XOMTools.setTextChild(group, "dispiroter" + group.getValue());
	}

	/**
	 * The features between the polyCyclicSpiroDescriptor and the first group element, or beween the STRUCTURALOPENBRACKET_EL and STRUCTURALCLOSEBRACKET_EL
	 * are found and then passed to resolveFeaturesOntoGroup
	 * @param state
	 * @param polyCyclicSpiroDescriptor
	 * @throws StructureBuildingException
	 * @throws ComponentGenerationException 
	 */
	private void determineFeaturesToResolveInSingleComponentSpiro(BuildState state, Element polyCyclicSpiroDescriptor) throws StructureBuildingException, ComponentGenerationException {
		Element possibleOpenBracket = (Element) XOMTools.getNextSibling(polyCyclicSpiroDescriptor);
		List<Element> elementsToResolve;
		if (possibleOpenBracket.getLocalName().equals(STRUCTURALOPENBRACKET_EL)){
			possibleOpenBracket.detach();
			elementsToResolve = XOMTools.getSiblingsUpToElementWithTagName(polyCyclicSpiroDescriptor, STRUCTURALCLOSEBRACKET_EL);
			XOMTools.getNextSibling(elementsToResolve.get(elementsToResolve.size()-1)).detach();//detach close bracket
		}
		else{
			elementsToResolve = XOMTools.getSiblingsUpToElementWithTagName(polyCyclicSpiroDescriptor, GROUP_EL);
		}
		resolveFeaturesOntoGroup(state, elementsToResolve);
	}
	
	/**
	 * Given some elements including a group element resolves all locanted and unlocanted features.
	 * @param state
	 * @param elementsToResolve
	 * @throws StructureBuildingException 
	 * @throws ComponentGenerationException 
	 */
	private void resolveFeaturesOntoGroup(BuildState state, List<Element> elementsToResolve) throws StructureBuildingException, ComponentGenerationException{
		if (elementsToResolve.size()==0){
			return;
		}
		Element substituentToResolve = new Element(SUBSTITUENT_EL);//temporary element containing elements that should be resolved before the ring is cloned
		Element parent = (Element) elementsToResolve.get(0).getParent();
		int index = parent.indexOf(elementsToResolve.get(0));
		Element group =null;
		for (Element element : elementsToResolve) {
			if (element.getLocalName().equals(GROUP_EL)){
				group = element;
			}
			element.detach();
			substituentToResolve.appendChild(element);
		}
		if (group ==null){
			throw new ComponentGenerationException("OPSIN bug: group element should of been given to method");
		}
		if (substituentToResolve.getChildElements().size()!=0){
			StructureBuildingMethods.resolveLocantedFeatures(state, substituentToResolve);
			StructureBuildingMethods.resolveUnLocantedFeatures(state, substituentToResolve);
			Elements children = substituentToResolve.getChildElements();
			for (int i = children.size() -1; i>=0; i--) {
				Element child = children.get(i);
				child.detach();
				parent.insertChild(child, index);
			}
		}
	}


	/**
	 * Processes bridges e.g. 4,7-methanoindene
	 * Resolves and attaches said bridges to the adjacent ring fragment
	 * @param state
	 * @param subOrRoot
	 * @throws StructureBuildingException 
	 */
	private void processFusedRingBridges(BuildState state, Element subOrRoot) throws StructureBuildingException {
		List<Element> bridges = XOMTools.getChildElementsWithTagName(subOrRoot, FUSEDRINGBRIDGE_EL);
		for (Element bridge : bridges) {
			Fragment ringFrag = state.xmlFragmentMap.get(XOMTools.getNextSibling(bridge, GROUP_EL));
			Fragment bridgeFrag =state.fragManager.buildSMILES(bridge.getAttributeValue(VALUE_ATR), ringFrag.getType(), ringFrag.getSubType(), NONE_LABELS_VAL);//TODO label bridges

			List<Atom> bridgeAtomList =bridgeFrag.getAtomList();
			bridgeFrag.addOutAtom(bridgeAtomList.get(0), 1, true);
			bridgeFrag.addOutAtom(bridgeAtomList.get(bridgeAtomList.size()-1), 1, true);
			Element possibleLocant = (Element) XOMTools.getPreviousSibling(bridge);
			if (possibleLocant !=null && possibleLocant.getLocalName().equals(LOCANT_EL)){
				String[] locantArray = matchComma.split(possibleLocant.getValue());
				if (locantArray.length==2){
					bridgeFrag.getOutAtom(0).setLocant(locantArray[0]);
					bridgeFrag.getOutAtom(1).setLocant(locantArray[1]);
					possibleLocant.detach();
				}
				StructureBuildingMethods.formEpoxide(state, bridgeFrag, ringFrag.getDefaultInAtom());
			}
			else{
				StructureBuildingMethods.formEpoxide(state, bridgeFrag, ringFrag.getAtomOrNextSuitableAtomOrThrow(ringFrag.getDefaultInAtom(), 1, true));
			}
			state.fragManager.incorporateFragment(bridgeFrag, ringFrag);
			bridge.detach();
		}
	}


	/**
	 * Searches for lambdaConvention elements and applies the valency they specify to the atom
	 * they specify on the substituent/root's fragment
	 * @param state
	 * @param subOrRoot
	 * @throws StructureBuildingException
	 */
	private void applyLambdaConvention(BuildState state, Element subOrRoot) throws StructureBuildingException {
		List<Element> lambdaConventionEls = XOMTools.getChildElementsWithTagName(subOrRoot, LAMBDACONVENTION_EL);
		for (Element lambdaConventionEl : lambdaConventionEls) {
			Fragment frag = state.xmlFragmentMap.get(subOrRoot.getFirstChildElement(GROUP_EL));
			if (lambdaConventionEl.getAttribute(LOCANT_ATR)!=null){
				frag.getAtomByLocantOrThrow(lambdaConventionEl.getAttributeValue(LOCANT_ATR)).setLambdaConventionValency(Integer.parseInt(lambdaConventionEl.getAttributeValue(LAMBDA_ATR)));
			}
			else{
				if (frag.getAtomList().size()!=1){
					throw new StructureBuildingException("Ambiguous use of lambda convention. Fragment has more than 1 atom but no locant was specified for the lambda");
				}
				frag.getFirstAtom().setLambdaConventionValency(Integer.parseInt(lambdaConventionEl.getAttributeValue(LAMBDA_ATR)));
			}
			lambdaConventionEl.detach();
		}
	}


	/**
	 * Uses the number of outAtoms that are present to assign the number of outAtoms on substituents that can have a variable number of outAtoms
	 * Hence at this point it can be determined if a multi radical susbtituent is present in the name
	 * This would be expected in multiplicative nomenclature and is noted in the state so that the StructureBuilder knows to resolve the
	 * section of the name from that point onwards in a left to right manner rather than right to left
	 * @param state
	 * @param subOrRoot: The sub/root to look in
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void handleMultiRadicals(BuildState state, Element subOrRoot) throws ComponentGenerationException, StructureBuildingException{
		Element group =subOrRoot.getFirstChildElement(GROUP_EL);
		String groupValue =group.getValue();
		Fragment thisFrag = state.xmlFragmentMap.get(group);
		if (groupValue.equals("methylene") || matchChalcogenReplacement.matcher(groupValue).matches()){//resolves for example trimethylene to propan-1,3-diyl or dithio to disulfan-1,2-diyl. Locants may not be specified before the multiplier
			Element beforeGroup =(Element) XOMTools.getPreviousSibling(group);
			if (beforeGroup!=null && beforeGroup.getLocalName().equals(MULTIPLIER_ATR) && beforeGroup.getAttributeValue(TYPE_ATR).equals(BASIC_TYPE_VAL) && XOMTools.getPreviousSibling(beforeGroup)==null){
				int multiplierVal = Integer.parseInt(beforeGroup.getAttributeValue(VALUE_ATR));
				if (!unsuitableForFormingChainMultiradical(group, beforeGroup)){
					if (groupValue.equals("methylene")){
						group.getAttribute(VALUE_ATR).setValue(StringTools.multiplyString("C", multiplierVal));
					}
					else if (groupValue.equals("thio")){
						group.getAttribute(VALUE_ATR).setValue(StringTools.multiplyString("S", multiplierVal));
					}
					else if (groupValue.equals("seleno")){
						group.getAttribute(VALUE_ATR).setValue(StringTools.multiplyString("[SeH?]", multiplierVal));
					}
					else if (groupValue.equals("telluro")){
						group.getAttribute(VALUE_ATR).setValue(StringTools.multiplyString("[TeH?]", multiplierVal));
					}
					else{
						throw new ComponentGenerationException("unexpected group value");
					}
					group.getAttribute(OUTIDS_ATR).setValue("1,"+Integer.parseInt(beforeGroup.getAttributeValue(VALUE_ATR)));
					XOMTools.setTextChild(group, beforeGroup.getValue() + groupValue);
					beforeGroup.detach();
					if (group.getAttribute(LABELS_ATR)!=null){//use standard numbering
						group.getAttribute(LABELS_ATR).detach();
					}
					state.fragManager.removeFragment(thisFrag);
					thisFrag =resolveGroup(state, group);
					state.xmlFragmentMap.put(group, thisFrag);
					group.removeAttribute(group.getAttribute(USABLEASJOINER_ATR));
				}
			}
		}

		if (group.getAttribute(OUTIDS_ATR)!=null){//adds outIDs at the specified atoms
			String[] radicalPositions = matchComma.split(group.getAttributeValue(OUTIDS_ATR));
			int firstIdInFrag =thisFrag.getIdOfFirstAtom();
            for (String radicalID : radicalPositions) {
                thisFrag.addOutAtom(firstIdInFrag + Integer.parseInt(radicalID) - 1, 1, true);
            }
		}
		int outAtomCount = thisFrag.getOutAtoms().size();
		if (outAtomCount >=2){
			if (groupValue.equals("amine")){//amine is a special case as it shouldn't technically be allowed but is allowed due to it's common usage in EDTA
				Element previousGroup =(Element) OpsinTools.getPreviousGroup(group);
				if (previousGroup==null || state.xmlFragmentMap.get(previousGroup).getOutAtoms().size() < 2){//must be preceded by a multi radical
					throw new ComponentGenerationException("Invalid use of amine as a substituent!");
				}
			}
			if (state.currentWordRule == WordRule.polymer){
				if (outAtomCount >=3){//In poly mode nothing may have more than 2 outAtoms e.g. nitrilo is -N= or =N-
					int valency =0;
					for (int i = 2; i < outAtomCount; i++) {
						OutAtom nextOutAtom = thisFrag.getOutAtom(i);
						valency += nextOutAtom.getValency();
						thisFrag.removeOutAtom(nextOutAtom);
					}
					thisFrag.getOutAtom(1).setValency(thisFrag.getOutAtom(1).getValency() + valency);
				}
			}
		}
		
		if (outAtomCount ==2 && EPOXYLIKE_SUBTYPE_VAL.equals(group.getAttributeValue(SUBTYPE_ATR))){
			Element possibleLocant =(Element) XOMTools.getPreviousSibling(group);
			if (possibleLocant !=null){
				String[] locantValues = matchComma.split(possibleLocant.getValue());
				if (locantValues.length==2){
					thisFrag.getOutAtom(0).setLocant(locantValues[0]);
					thisFrag.getOutAtom(1).setLocant(locantValues[1]);
					possibleLocant.detach();
					subOrRoot.addAttribute(new Attribute(LOCANT_ATR, locantValues[0]));
				}
			}
		}

		int totalOutAtoms = outAtomCount + calculateOutAtomsToBeAddedFromInlineSuffixes(state, group, subOrRoot.getChildElements(SUFFIX_EL));
		if (totalOutAtoms >= 2){
			group.addAttribute(new Attribute (ISAMULTIRADICAL_ATR, Integer.toString(totalOutAtoms)));
		}
	}

	/**
	 * Checks for cases where multiplier(methylene) or multiplier(thio) and the like should not be interpreted as one fragment
	 * Something like nitrilotrithiotriacetic acid or oxetane-3,3-diyldimethylene
	 * @param group
	 * @param multiplierBeforeGroup 
	 * @return
	 */
	private boolean unsuitableForFormingChainMultiradical(Element group, Element multiplierBeforeGroup) {
		Element previousGroup = (Element) OpsinTools.getPreviousGroup(group);
		if (previousGroup!=null && previousGroup.getAttribute(ISAMULTIRADICAL_ATR)!=null){
			if (previousGroup.getAttributeValue(ACCEPTSADDITIVEBONDS_ATR)!=null && XOMTools.getPreviousSibling(previousGroup.getParent())!=null){
				return false;
			}
			//the initial multiplier is proceded by another multiplier e.g. bis(dithio)
			if (((Element)XOMTools.getPrevious(multiplierBeforeGroup)).getLocalName().equals(MULTIPLIER_EL)){
				return false;
			}
			if (previousGroup.getAttributeValue(ISAMULTIRADICAL_ATR).equals(multiplierBeforeGroup.getAttributeValue(VALUE_ATR))){
				return true;//probably multiplicative
			}
			else{
				return false;
			}
		}
		return false;
	}


	/**
	 * Calculates number of OutAtoms that the resolveSuffixes method will add.
	 * @param state
	 * @param group
	 * @param suffixes
	 * @return numberOfOutAtoms that will be added by resolveSuffixes
	 * @throws ComponentGenerationException
	 */
	private int calculateOutAtomsToBeAddedFromInlineSuffixes(BuildState state, Element group, Elements suffixes) throws  ComponentGenerationException {
		int outAtomsThatWillBeAdded = 0;
		Fragment frag = state.xmlFragmentMap.get(group);
		String groupType = frag.getType();
		String subgroupType = frag.getSubType();
		String suffixTypeToUse =null;
		if (suffixApplicability.containsKey(groupType)){
			suffixTypeToUse =groupType;
		}
		else{
			suffixTypeToUse = STANDARDGROUP_TYPE_VAL;
		}

		List<Fragment> suffixList =state.xmlSuffixMap.get(group);

		for (Fragment suffix : suffixList) {
			outAtomsThatWillBeAdded += suffix.getOutAtoms().size();
		}
		for(int i=0;i<suffixes.size();i++) {
			Element suffix = suffixes.get(i);
			String suffixValue = suffix.getAttributeValue(VALUE_ATR);
			Elements suffixRuleTags =getSuffixRuleTags(suffixTypeToUse, suffixValue, subgroupType);
			for(int j=0;j<suffixRuleTags.size();j++) {
				Element suffixRuleTag = suffixRuleTags.get(j);
				String suffixRuleTagName =suffixRuleTag.getLocalName();
				if(suffixRuleTagName.equals(SUFFIXRULES_SETOUTATOM_EL)) {
					outAtomsThatWillBeAdded +=1;
				}
			}
		}
		return outAtomsThatWillBeAdded;
	}

	/**
	 * Corrects something like L-alanyl-L-glutaminyl-L-arginyl-O-phosphono-L-seryl-L-alanyl-L-proline to:
	 * ((((L-alanyl-L-glutaminyl)-L-arginyl)-O-phosphono-L-seryl)-L-alanyl)-L-proline
	 * i.e. substituents go onto the last mentioned amino acid; amino acids chain together to form peptides
	 * @param state
	 * @param groups
	 * @param brackets
	 */
	private void addImplicitBracketsToAminoAcids(BuildState state, List<Element> groups, List<Element> brackets) {
		for (int i = groups.size() -1; i >=0; i--) {
			Element group = groups.get(i);
			if (group.getAttributeValue(TYPE_ATR).equals(AMINOACID_TYPE_VAL)){
				Element subOrRoot = (Element) group.getParent();
				
				//now find the brackets/substituents before this element
				Element previous = (Element) XOMTools.getPreviousSibling(subOrRoot);
				List<Element> previousElements = new ArrayList<Element>();
				while( previous !=null){
					if (!previous.getLocalName().equals(SUBSTITUENT_EL) && !previous.getLocalName().equals(BRACKET_EL)){
						break;
					}
					previousElements.add(previous);
					previous = (Element) XOMTools.getPreviousSibling(previous);
				}
				if (previousElements.size()>0){//an implicit bracket is needed
					Collections.reverse(previousElements);
					Element bracket = new Element(BRACKET_EL);
					bracket.addAttribute(new Attribute(TYPE_ATR, IMPLICIT_TYPE_VAL));
					Element parent = (Element) subOrRoot.getParent();
					int indexToInsertAt = parent.indexOf(previousElements.get(0));
					for (Element element : previousElements) {
						element.detach();
						bracket.appendChild(element);
					}

					subOrRoot.detach();
					bracket.appendChild(subOrRoot);
					parent.insertChild(bracket, indexToInsertAt);
					brackets.add(bracket);
				}
			}
		}
	}


	/**Looks for places where brackets should have been, and does the same
	 * as findAndStructureBrackets. E.g. dimethylaminobenzene -> (dimethylamino)benzene.
	 * The bracketting in the above case occurs when the substituent that is being procesed is the amino group
	 * @param state
	 * @param brackets
	 * @param substituents: An arraylist of substituent elements
	 * @return Whether the method did something, and so needs to be called again.
	 * @throws StructureBuildingException
     * @throws ComponentGenerationException
	 */
	private void findAndStructureImplictBrackets(BuildState state, List<Element> substituents, List<Element> brackets) throws ComponentGenerationException, StructureBuildingException {
		for (Element substituent : substituents) {//will attempt to bracket this substituent with the substituent before it
			String firstElInSubName =((Element)substituent.getChild(0)).getLocalName();
			if (firstElInSubName.equals(LOCANT_EL) ||firstElInSubName.equals(MULTIPLIER_EL)){
				continue;
			}

			Element substituentGroup = substituent.getFirstChildElement(GROUP_EL);
			String theSubstituentSubType = substituentGroup.getAttributeValue(SUBTYPE_ATR);
			String theSubstituentType = substituentGroup.getAttributeValue(TYPE_ATR);
			//Only some substituents are valid joiners (e.g. no rings are valid joiners). Need to be atleast bivalent.
			if (substituentGroup.getAttribute(USABLEASJOINER_ATR)==null){
				continue;
			}
			Fragment frag =state.xmlFragmentMap.get(substituentGroup);

			//there must be an element after the substituent for the implicit bracket to be required
			Element elementAftersubstituent =(Element)XOMTools.getNextSibling(substituent);
			if (elementAftersubstituent ==null ||
					!elementAftersubstituent.getLocalName().equals(SUBSTITUENT_EL) &&
					!elementAftersubstituent.getLocalName().equals(BRACKET_EL) &&
					!elementAftersubstituent.getLocalName().equals(ROOT_EL)){
				continue;
			}

			//checks that the element before is a substituent or a bracket which will obviously include substituent/s
			//this makes sure there's more than just a substituent in the bracket
			Element elementBeforeSubstituent =(Element)XOMTools.getPreviousSibling(substituent);
			if (elementBeforeSubstituent ==null||
					!elementBeforeSubstituent.getLocalName().equals(SUBSTITUENT_EL) &&
					!elementBeforeSubstituent.getLocalName().equals(BRACKET_EL)){
				continue;
			}
			
			//Not preceded and succeded by a bracket e.g. Not (benzyl)methyl(phenyl)amine	c.f. P-16.4.1.3 (draft 2004)
			if (elementBeforeSubstituent.getLocalName().equals(BRACKET_EL) && !IMPLICIT_TYPE_VAL.equals(elementBeforeSubstituent.getAttributeValue(TYPE_ATR)) && elementAftersubstituent.getLocalName().equals(BRACKET_EL)){
				Element firstChildElementOfElementAfterSubstituent = (Element) elementAftersubstituent.getChild(0);
				if ((firstChildElementOfElementAfterSubstituent.getLocalName().equals(SUBSTITUENT_EL) || firstChildElementOfElementAfterSubstituent.getLocalName().equals(BRACKET_EL))
					&& !((Element)XOMTools.getPrevious(firstChildElementOfElementAfterSubstituent)).getLocalName().equals(HYPHEN_EL)){
					continue;
				}
			}

			//look for hyphen between substituents, this seems to indicate implicit bracketing was not desired e.g. dimethylaminomethane vs dimethyl-aminomethane
			Element elementDirectlyBeforeSubstituent = (Element) XOMTools.getPrevious(substituent.getChild(0));//can't return null as we know elementBeforeSubstituent is not null
			if (elementDirectlyBeforeSubstituent.getLocalName().equals(HYPHEN_EL)){
				continue;
			}

			//prevents alkyl chains being bracketed together e.g. ethylmethylamine
			//...unless it's something like 2-methylethyl where the first appears to be locanted onto the second
			List<Element> groupElements  = XOMTools.getDescendantElementsWithTagName(elementBeforeSubstituent, GROUP_EL);//one for a substituent, possibly more for a bracket
			Element lastGroupOfElementBeforeSub =groupElements.get(groupElements.size()-1);
			if (lastGroupOfElementBeforeSub==null){throw new ComponentGenerationException("No group where group was expected");}
			if (theSubstituentType.equals(CHAIN_TYPE_VAL) && theSubstituentSubType.equals(ALKANESTEM_SUBTYPE_VAL) &&
					lastGroupOfElementBeforeSub.getAttributeValue(TYPE_ATR).equals(CHAIN_TYPE_VAL) && lastGroupOfElementBeforeSub.getAttributeValue(SUBTYPE_ATR).equals(ALKANESTEM_SUBTYPE_VAL)){
				boolean placeInImplicitBracket =false;

				Element suffixAfterGroup=(Element)XOMTools.getNextSibling(lastGroupOfElementBeforeSub, SUFFIX_EL);
				//if the alkane ends in oxy, sulfinyl, sulfonyl etc. it's not a pure alkane (other suffixes don't need to be considered as they would produce silly structures)
				if (suffixAfterGroup !=null && matchInlineSuffixesThatAreAlsoGroups.matcher(suffixAfterGroup.getValue()).matches()){
					placeInImplicitBracket =true;
				}
				//look for locants and check whether they appear to be referring to the other chain
				if (!placeInImplicitBracket){
					Elements childrenOfElementBeforeSubstituent  =elementBeforeSubstituent.getChildElements();
					Boolean foundLocantNotReferringToChain =null;
					for (int i = 0; i < childrenOfElementBeforeSubstituent.size(); i++) {
						String currentElementName = childrenOfElementBeforeSubstituent.get(i).getLocalName();
						if (currentElementName.equals(LOCANT_EL)){
							String locantText =childrenOfElementBeforeSubstituent.get(i).getValue();
							if(!frag.hasLocant(locantText)){
								foundLocantNotReferringToChain=true;
								break;
							}
							else{
								foundLocantNotReferringToChain=false;
							}
						}
						else if (currentElementName.equals(STEREOCHEMISTRY_EL)){
						}
						else{
							break;
						}
					}
					if (foundLocantNotReferringToChain !=null && !foundLocantNotReferringToChain){//a locant was found and it appeared to refer to the other chain
						placeInImplicitBracket=true;
					}
				}
				if (!placeInImplicitBracket){
					continue;
				}
			}

			//prevent bracketing to multi radicals unless through substitution they are likely to cease being multiradicals
			if (lastGroupOfElementBeforeSub.getAttribute(ISAMULTIRADICAL_ATR)!=null && lastGroupOfElementBeforeSub.getAttribute(ACCEPTSADDITIVEBONDS_ATR)==null){
				continue;
			}
			//prevent bracketting perhalogeno terms 
			if (PERHALOGENO_SUBTYPE_VAL.equals(lastGroupOfElementBeforeSub.getAttributeValue(SUBTYPE_ATR))){
				continue;
			}

			/*
			 * locant may need to be moved. This occurs when the group in elementBeforeSubstituent is not supposed to be locanted onto
			 *  theSubstituentGroup
			 *  e.g. 2-aminomethyl-1-chlorobenzene where the 2 refers to the benzene NOT the methyl
			 */
			List<Element> locantRelatedElements = new ArrayList<Element>();//sometimes moved
			String[] locantValues = null;
			ArrayList<Element> stereoChemistryElements =new ArrayList<Element>();//always moved if bracketing occurs
			Elements childrenOfElementBeforeSubstituent = elementBeforeSubstituent.getChildElements();
			for (int i = 0; i < childrenOfElementBeforeSubstituent.size(); i++) {
				String currentElementName = childrenOfElementBeforeSubstituent.get(i).getLocalName();
				if (currentElementName.equals(STEREOCHEMISTRY_EL)){
					stereoChemistryElements.add(childrenOfElementBeforeSubstituent.get(i));
				}
				else if (currentElementName.equals(LOCANT_EL)){
					if (locantValues !=null){
						break;
					}
					locantRelatedElements.add(childrenOfElementBeforeSubstituent.get(i));
					locantValues = matchComma.split(childrenOfElementBeforeSubstituent.get(i).getValue());
				}
				else{
					break;
				}
			}

			//either all locants will be moved, or none
			Boolean moveLocants = false;
			if (locantValues!=null){
				Element elAfterLocant = (Element) XOMTools.getNextSibling(locantRelatedElements.get(0));
				for (String locantText : locantValues) {
					if (elAfterLocant.getAttribute(FRONTLOCANTSEXPECTED_ATR)!=null && StringTools.arrayToList(matchComma.split(elAfterLocant.getAttributeValue(FRONTLOCANTSEXPECTED_ATR))).contains(locantText)){
						continue;
					}
					
					//Check the right fragment in the bracket:
					//if it only has 1 then assume locanted substitution onto it not intended. Or if doesn't have the required locant
					if (frag.getAtomList().size()==1 ||	!frag.hasLocant(locantText) || matchElementSymbolOrAminoAcidLocant.matcher(locantText).find()){
						if (checkLocantPresentOnPotentialRoot(state, substituent, locantText)){
							moveLocants =true;//locant location is present elsewhere
						}
						else if (findElementsMissingIndirectLocants(elementBeforeSubstituent, locantRelatedElements.get(0)).size()==0 || !state.xmlFragmentMap.get(lastGroupOfElementBeforeSub).hasLocant(locantText)){
							moveLocants =true;//the fragment adjacent to the locant doesn't have this locant or doesn't need any indirect locants. Assume it will appear elsewhere later
						}
					}
				}
			}

			if (moveLocants && locantValues !=null && locantValues.length >1){
				Element shouldBeAMultiplierNode = (Element)XOMTools.getNextSibling(locantRelatedElements.get(0));
				if (shouldBeAMultiplierNode !=null && shouldBeAMultiplierNode.getLocalName().equals(MULTIPLIER_EL)){
					Element shouldBeAGroupOrSubOrBracket = (Element)XOMTools.getNextSibling(shouldBeAMultiplierNode);
					if (shouldBeAGroupOrSubOrBracket !=null){
						if ((shouldBeAGroupOrSubOrBracket.getLocalName().equals(GROUP_EL) && shouldBeAMultiplierNode.getAttributeValue(TYPE_ATR).equals(GROUP_TYPE_VAL))//e.g. 2,5-bisaminothiobenzene --> 2,5-bis(aminothio)benzene
								|| (matchInlineSuffixesThatAreAlsoGroups.matcher(substituentGroup.getValue()).matches())){//e.g. 4,4'-dimethoxycarbonyl-2,2'-bioxazole --> 4,4'-di(methoxycarbonyl)-2,2'-bioxazole
							locantRelatedElements.add(shouldBeAMultiplierNode);//e.g. 1,5-bis-(4-methylphenyl)sulfonyl --> 1,5-bis-((4-methylphenyl)sulfonyl)
						}
						else if (ORTHOMETAPARA_TYPE_VAL.equals(locantRelatedElements.get(0).getAttributeValue(TYPE_ATR))){//e.g. p-dimethylamino[ring]
							XOMTools.setTextChild(locantRelatedElements.get(0), locantValues[1]);
						}
						else{//don't bracket other complex multiplied substituents (name hasn't given enough hints if indeed bracketing was expected)
							continue;
						}
					}
					else{
						moveLocants =false;
					}
				}
				else{
					moveLocants =false;
				}
			}

			Element bracket = new Element(BRACKET_EL);
			bracket.addAttribute(new Attribute(TYPE_ATR, IMPLICIT_TYPE_VAL));

            for (Element stereoChemistryElement : stereoChemistryElements) {
            	stereoChemistryElement.detach();
                bracket.appendChild(stereoChemistryElement);
            }
			if (moveLocants){
                for (Element locantElement : locantRelatedElements) {
                    locantElement.detach();
                    bracket.appendChild(locantElement);
                }
			}

			/*
			 * Case when a multiplier should be moved
			 * e.g. tripropan-2-yloxyphosphane -->tri(propan-2-yloxy)phosphane or trispropan-2-ylaminophosphane --> tris(propan-2-ylamino)phosphane
			 */
			if (locantRelatedElements.size()==0){
				Element possibleMultiplier =childrenOfElementBeforeSubstituent.get(0);
				if (possibleMultiplier.getLocalName().equals(MULTIPLIER_EL) && (
						matchInlineSuffixesThatAreAlsoGroups.matcher(substituentGroup.getValue()).matches() || possibleMultiplier.getAttributeValue(TYPE_ATR).equals(GROUP_TYPE_VAL))){
					Element desiredGroup = XOMTools.getNextSiblingIgnoringCertainElements(possibleMultiplier, new String[]{MULTIPLIER_EL});
					if (desiredGroup !=null && desiredGroup.getLocalName().equals(GROUP_EL)){
						childrenOfElementBeforeSubstituent.get(0).detach();
						bracket.appendChild(childrenOfElementBeforeSubstituent.get(0));
					}
				}
			}

			Element parent = (Element)substituent.getParent();
			int startIndex=parent.indexOf(elementBeforeSubstituent);
			int endIndex=parent.indexOf(substituent);
			for(int i = 0 ; i <= (endIndex-startIndex);i++) {
				Node n = parent.getChild(startIndex);
				n.detach();
				bracket.appendChild(n);
			}
			parent.insertChild(bracket, startIndex);
			brackets.add(bracket);
		}
	}


	/** 
	 * Attempts to match locants to non adjacent suffixes/unsatuators
	 * e.g.  2-propanol, 3-furyl, 2'-Butyronaphthone
	 * @param state
	 * @param subOrRoot The substituent/root to look for locants in.
	 * @throws StructureBuildingException
	 */
	private void matchLocantsToIndirectFeatures(BuildState state, Element subOrRoot) throws  StructureBuildingException {
		/* Root fragments (or the root in a bracket) can have prefix-locants
		 * that work on suffixes - (2-furyl), 2-propanol, (2-propylmethyl), (2-propyloxy), 2'-Butyronaphthone.
		 */
		Elements children = subOrRoot.getChildElements();
		List<Element> locantEls = new ArrayList<Element>();
		for (int i = 0; i < children.size(); i++) {
			Element el = children.get(i);
			if (el.getLocalName().equals(LOCANT_EL)){
				Element afterLocant =(Element) XOMTools.getNextSibling(el);
				if (afterLocant!=null && afterLocant.getLocalName().equals(MULTIPLIER_EL)){//locant should not be followed by a multiplier. c.f. 1,2,3-tributyl 2-acetyloxypropane-1,2,3-tricarboxylate
					continue;
				}
				locantEls.add(el);
			}
			else if (el.getLocalName().equals(GROUP_EL)){
				break;
			}
		}

		if (locantEls.size()>0){
			Element group =subOrRoot.getFirstChildElement(GROUP_EL);
			Element lastLocant = locantEls.get(locantEls.size()-1);//the locant that may apply to an unsaturator/suffix
			String[] locantValues = matchComma.split(lastLocant.getValue());
			if (locantValues.length==1 && group.getAttribute(FRONTLOCANTSEXPECTED_ATR)!=null){//some trivial retained names like 2-furyl expect locants to be in front of them. For these the indirect intepretation will always be used rather than checking whether 2-(furyl) even makes sense
				String[] allowedLocants = matchComma.split(group.getAttributeValue(FRONTLOCANTSEXPECTED_ATR));
				for (String allowedLocant : allowedLocants) {
					if (locantValues[0].equals(allowedLocant)){
						Element expectedSuffix =(Element) XOMTools.getNextSibling(group);
						if (expectedSuffix!=null && expectedSuffix.getLocalName().equals(SUFFIX_EL) && expectedSuffix.getAttribute(LOCANT_ATR)==null){
							expectedSuffix.addAttribute(new Attribute(LOCANT_ATR, locantValues[0]));
							lastLocant.detach();
							return;
						}
						break;
					}
				}
			}
			boolean allowIndirectLocants =true;
			if(state.currentWordRule == WordRule.multiEster && !ADDEDHYDROGENLOCANT_TYPE_VAL.equals(lastLocant.getAttributeValue(TYPE_ATR))){//special case e.g. 1-benzyl 4-butyl terephthalate (locants do not apply to yls)
				Element parentEl=(Element) subOrRoot.getParent();
				if (parentEl.getLocalName().equals(WORD_EL) && parentEl.getAttributeValue(TYPE_ATR).equals(SUBSTITUENT_EL) && parentEl.getChildCount()==1 &&
						locantValues.length==1 && !ORTHOMETAPARA_TYPE_VAL.equals(lastLocant.getAttributeValue(TYPE_ATR))){
					allowIndirectLocants =false;
				}
			}
			Fragment fragmentAfterLocant =state.xmlFragmentMap.get(group);
			if (fragmentAfterLocant.getAtomList().size()<=1){
				allowIndirectLocants =false;//e.g. prevent 1-methyl as meth-1-yl is extremely unlikely to be the intended result
			}

			if (allowIndirectLocants){
				/* The first locant is most likely a locant indicating where this subsituent should be attached.
				 * If the locant cannot be found on a potential root this cannot be the case though (assuming the name is valid of course)
				 */
				if (!ADDEDHYDROGENLOCANT_TYPE_VAL.equals(lastLocant.getAttributeValue(TYPE_ATR)) && locantEls.size() ==1 &&
						locantValues.length == 1 && checkLocantPresentOnPotentialRoot(state, subOrRoot, locantValues[0]) && XOMTools.getPreviousSibling(lastLocant, LOCANT_EL)==null){
					return;
				}
				boolean assignableToIndirectFeatures =true;
				List<Element> locantAble  =findElementsMissingIndirectLocants(subOrRoot, lastLocant);
				if (locantAble.size() < locantValues.length){
					assignableToIndirectFeatures =false;
				}
				else{
					for (String locantValue : locantValues) {
						if (!fragmentAfterLocant.hasLocant(locantValue)){//locant is not available on the group adjacent to the locant!
							assignableToIndirectFeatures =false;
						}
					}
				}
				
				if (!assignableToIndirectFeatures){//usually indicates the name will fail unless the suffix has the locant or heteroatom replacement will create the locant
					if (locantValues.length==1){
						List<Fragment> suffixes =state.xmlSuffixMap.get(group);
						//I do not want to assign element locants as in locants on the suffix as I currently know of no examples where this actually occurs
						if (matchElementSymbolOrAminoAcidLocant.matcher(locantValues[0]).matches()){
							return;
						}
						for (Fragment suffix : suffixes) {
							if (suffix.hasLocant(locantValues[0])){//e.g. 2'-Butyronaphthone
								Atom dummyRAtom =suffix.getFirstAtom();
								List<Atom> neighbours =dummyRAtom.getAtomNeighbours();
								Bond b =null;
								atomLoop: for (Atom atom : neighbours) {
									List<String> neighbourLocants = atom.getLocants();
									for (String neighbourLocant : neighbourLocants) {
										if (matchNumericLocant.matcher(neighbourLocant).matches()){
											b=suffix.findBondOrThrow(dummyRAtom, atom);
											break atomLoop;
										}
									}
								}
								if (b!=null){
									state.fragManager.removeBond(b);//the current bond between the dummy R and the suffix
									state.fragManager.createBond(dummyRAtom, suffix.getAtomByLocantOrThrow(locantValues[0]), b.getOrder());
									lastLocant.detach();
								}
							}
						}
					}
				}
				else{
					for (int i = 0; i < locantValues.length; i++) {
						String locantValue = locantValues[i];
						locantAble.get(i).addAttribute(new Attribute(LOCANT_ATR, locantValue));
					}
					lastLocant.detach();
				}
			}
		}
	}


	/**
	 * Find elements that can have indirect locants but don't currently
	 * This requirement excludes hydro and heteroatoms as it is assumed that locants for these are always adjacent (or handled by the special HW code in the case of heteroatoms)
	 * @param subOrRoot The subOrRoot of interest
	 * @param locantEl the locant, only elements after it will be considered
	 * @return An arrayList of locantable elements
	 */
	private List<Element> findElementsMissingIndirectLocants(Element subOrRoot,Element locantEl) {
		List<Element> locantAble = new ArrayList<Element>();
		Elements childrenOfSubOrBracketOrRoot=subOrRoot.getChildElements();
		for (int j = 0; j < childrenOfSubOrBracketOrRoot.size(); j++) {
			Element el =childrenOfSubOrBracketOrRoot.get(j);
			String name =el.getLocalName();
			if (name.equals(SUFFIX_EL) || name.equals(UNSATURATOR_EL) || name.equals(CONJUNCTIVESUFFIXGROUP_EL)){
				if (el.getAttribute(LOCANT_ATR) ==null && el.getAttribute(LOCANTID_ATR) ==null && el.getAttribute(MULTIPLIED_ATR)==null){// shouldn't already have a locant or be multiplied (should of already had locants assignd to it if that were the case)
					if (subOrRoot.indexOf(el)>subOrRoot.indexOf(locantEl)){
						if (name.equals(SUFFIX_EL)){//check a few special cases that must not be locanted
							Element group = (Element) XOMTools.getPreviousSibling(el, GROUP_EL);
							String type = group.getAttributeValue(TYPE_ATR);
							if (group.getValue().equals("phen")|| type.equals(ACIDSTEM_TYPE_VAL)|| type.equals(NONCARBOXYLICACID_TYPE_VAL) || type.equals(CHALCOGENACIDSTEM_TYPE_VAL)){
								continue;
							}
						}
						locantAble.add(el);
					}
				}
			}
		}
		return locantAble;
	}


	/**
	 * Put di-carbon modifying suffixes e.g. oic acids, aldehydes on opposite ends of chain
	 * @param state
	 * @param subOrRoot
	 * @throws StructureBuildingException
	 */
	private void assignImplicitLocantsToDiTerminalSuffixes(BuildState state, Element subOrRoot) throws StructureBuildingException {
		Element terminalSuffix1 = subOrRoot.getFirstChildElement(SUFFIX_EL);
		if (terminalSuffix1!=null){
			if (isATerminalSuffix(terminalSuffix1) && XOMTools.getNextSibling(terminalSuffix1) != null){
				Element terminalSuffix2 =(Element)XOMTools.getNextSibling(terminalSuffix1);
				if (isATerminalSuffix(terminalSuffix2)){
					Element hopefullyAChain = (Element) XOMTools.getPreviousSibling((Element)terminalSuffix1, GROUP_EL);
					if (hopefullyAChain != null && hopefullyAChain.getAttributeValue(TYPE_ATR).equals(CHAIN_TYPE_VAL)){
						int chainLength = state.xmlFragmentMap.get(hopefullyAChain).getChainLength();
						if (chainLength >=2){
							terminalSuffix1.addAttribute(new Attribute(LOCANT_ATR, "1"));
							terminalSuffix2.addAttribute(new Attribute(LOCANT_ATR, Integer.toString(chainLength)));
						}
					}
				}
			}
		}
	}


	/**
	 * Checks whether a suffix element is:
	 * a suffix, an inline suffix OR terminal root suffix, has no current locant
	 * @param suffix
	 * @return
	 */
	private boolean isATerminalSuffix(Element suffix){
        return suffix.getLocalName().equals(SUFFIX_EL) &&
                suffix.getAttribute(LOCANT_ATR) == null &&
                (suffix.getAttributeValue(TYPE_ATR).equals(INLINE_TYPE_VAL) || TERMINAL_SUBTYPE_VAL.equals(suffix.getAttributeValue(SUBTYPE_ATR)));
		}

	private void processConjunctiveNomenclature(BuildState state, Element subOrRoot) throws ComponentGenerationException, StructureBuildingException {
		List<Element> conjunctiveGroups = XOMTools.getChildElementsWithTagName(subOrRoot, CONJUNCTIVESUFFIXGROUP_EL);
		if (conjunctiveGroups.size()>0){
			Element ringGroup = subOrRoot.getFirstChildElement(GROUP_EL);
			Fragment ringFrag = state.xmlFragmentMap.get(ringGroup);
			if (ringFrag.getOutAtoms().size()!=0 ){
				throw new ComponentGenerationException("OPSIN Bug: Ring fragment should have no radicals");
			}
			List<Fragment> conjunctiveFragments = new ArrayList<Fragment>();
			for (Element group : conjunctiveGroups) {
				Fragment frag = state.xmlFragmentMap.get(group);
				conjunctiveFragments.add(frag);
			}
			if (conjunctiveGroups.size()==1){
				//label atoms appropriately
				List<Atom> atomList = conjunctiveFragments.get(0).getAtomList();
				boolean alphaIsPosition1 = atomList.get(0).getIncomingValency() < 3;
				int counter =0;
				for (int i = (alphaIsPosition1 ? 0 : 1); i < atomList.size(); i++) {
					Atom a = atomList.get(i);
					if (counter==0){
						a.addLocant("alpha");
					}
					else if (counter==1){
						a.addLocant("beta");
					}
					else if (counter==2){
						a.addLocant("gamma");
					}
					else if (counter==3){
						a.addLocant("delta");
					}
					else if (counter==4){
						a.addLocant("epsilon");
					}
					else if (counter==5){
						a.addLocant("zeta");
					}
					else if (counter==6){
						a.addLocant("eta");
					}
					counter++;
				}
			}
			for (int i = 0; i < conjunctiveFragments.size(); i++) {
				Fragment conjunctiveFragment = conjunctiveFragments.get(i);
				if (conjunctiveGroups.get(i).getAttribute(LOCANT_ATR)!=null){
					state.fragManager.createBond(lastNonSuffixCarbonWithSufficientValency(conjunctiveFragment), ringFrag.getAtomByLocantOrThrow(conjunctiveGroups.get(i).getAttributeValue(LOCANT_ATR)) , 1);
				}
				else{
					state.fragManager.createBond(lastNonSuffixCarbonWithSufficientValency(conjunctiveFragment), ringFrag.getAtomOrNextSuitableAtomOrThrow(ringFrag.getFirstAtom(), 1, true) , 1);
				}
				state.fragManager.incorporateFragment(conjunctiveFragment, ringFrag);
			}
		}
	}


	private Atom lastNonSuffixCarbonWithSufficientValency(Fragment conjunctiveFragment) throws ComponentGenerationException {
		List<Atom> atomList = conjunctiveFragment.getAtomList();
		for (int i = atomList.size()-1; i >=0; i--) {
			Atom a = atomList.get(i);
			if (a.getType().equals(SUFFIX_TYPE_VAL)){
				continue;
			}
			if (!a.getElement().equals("C")){
				continue;
			}
			if (ValencyChecker.checkValencyAvailableForBond(a, 1)){
				return a;
			}
		}
		throw new ComponentGenerationException("OPSIN Bug: Unable to find non suffix carbon with sufficient valency");
	}


	/**Process the effects of suffixes upon a fragment. 
	 * Unlocanted non-terminal suffixes are not attached yet. All other suffix effects are performed
	 * @param state
	 * @param group The group element for the fragment to which the suffixes will be added
	 * @param suffixes The suffix elements for a fragment.
	 * @throws StructureBuildingException If the suffixes can't be resolved properly.
	 * @throws ComponentGenerationException
	 */
	private void resolveSuffixes(BuildState state, Element group, List<Element> suffixes) throws StructureBuildingException, ComponentGenerationException {
		Fragment frag = state.xmlFragmentMap.get(group);
		int firstAtomID = frag.getIdOfFirstAtom();//typically equivalent to locant 1
		List<Atom> atomList =frag.getAtomList();//this instance of atomList will not change even once suffixes are merged into the fragment
		int defaultAtom =0;//indice in atomList
		String groupType = frag.getType();
		String subgroupType = frag.getSubType();
		String suffixTypeToUse =null;
		if (suffixApplicability.containsKey(groupType)){
			suffixTypeToUse =groupType;
		}
		else{
			suffixTypeToUse =STANDARDGROUP_TYPE_VAL;
		}

		List<Fragment> suffixList = state.xmlSuffixMap.get(group);
        for (Element suffix : suffixes) {
            String suffixValue = suffix.getAttributeValue(VALUE_ATR);

            String locant = StructureBuildingMethods.getLocant(suffix);
            int idOnParentFragToUse = 0;
            if (!locant.equals("0")) {
                idOnParentFragToUse = frag.getIDFromLocantOrThrow(locant);
            }
            if (idOnParentFragToUse == 0 && suffix.getAttribute(LOCANTID_ATR) != null) {
                idOnParentFragToUse = Integer.parseInt(suffix.getAttributeValue(LOCANTID_ATR));
            }
            if (idOnParentFragToUse == 0 && suffix.getAttribute(DEFAULTLOCANTID_ATR) != null) {
                idOnParentFragToUse = Integer.parseInt(suffix.getAttributeValue(DEFAULTLOCANTID_ATR));
            }
            if (idOnParentFragToUse == 0 && (suffixTypeToUse.equals(ACIDSTEM_TYPE_VAL) || suffixTypeToUse.equals(NONCARBOXYLICACID_TYPE_VAL) || suffixTypeToUse.equals(CHALCOGENACIDSTEM_TYPE_VAL))) {//means that e.g. sulfonyl has an explicit outAtom
                idOnParentFragToUse = firstAtomID;
            }

            Fragment suffixFrag = null;
            Elements suffixRuleTags = getSuffixRuleTags(suffixTypeToUse, suffixValue, subgroupType);
            for (int j = 0; j < suffixRuleTags.size(); j++) {
                Element suffixRuleTag = suffixRuleTags.get(j);
                String suffixRuleTagName = suffixRuleTag.getLocalName();
                if (defaultAtom >= atomList.size()) {
                    defaultAtom = 0;
                }
                if (suffixRuleTagName.equals(SUFFIXRULES_ADDGROUP_EL)) {
                    if (suffixFrag == null) {
                        if (suffixList.size() <= 0) {
                            throw new ComponentGenerationException("OPSIN Bug: Suffixlist should not be empty");
                        }
                        suffixFrag = suffixList.remove(0);//take the first suffix out of the list, it should of been added in the same order that it is now being read.

                        if (suffixFrag.getFirstAtom().getBonds().size() <= 0) {
                            throw new ComponentGenerationException("OPSIN Bug: Dummy atom in suffix should have at least one bond to it");
                        }
                        int bondOrderRequired = suffixFrag.getFirstAtom().getIncomingValency();
                        if (idOnParentFragToUse == 0) {
                            if (suffixRuleTag.getAttribute(SUFFIXRULES_KETONELOCANT_ATR) != null && !atomList.get(defaultAtom).getAtomIsInACycle()) {
                                if (defaultAtom == 0)
                                    defaultAtom = FragmentTools.findKetoneAtomIndice(frag, defaultAtom);
                                idOnParentFragToUse = atomList.get(defaultAtom).getID();
                                defaultAtom++;
                            } else {
                                idOnParentFragToUse = atomList.get(defaultAtom).getID();
                            }
                            idOnParentFragToUse = frag.getAtomOrNextSuitableAtomOrThrow(frag.getAtomByIDOrThrow(idOnParentFragToUse), bondOrderRequired, true).getID();
                        }

                        //create a new bond and associate it with the suffixfrag and both atoms. Remember the suffixFrag has not been imported into the frag yet
                        List<Bond> bonds = new ArrayList<Bond>(suffixFrag.getFirstAtom().getBonds());
                        for (Bond bondToSuffix : bonds) {
                            Atom suffixAtom;
                            if (bondToSuffix.getToAtom().getElement().equals("R")) {
                                suffixAtom = bondToSuffix.getFromAtom();
                            } else {
                                suffixAtom = bondToSuffix.getToAtom();
                            }
                            Atom parentfragAtom = frag.getAtomByIDOrThrow(idOnParentFragToUse);
                            state.fragManager.createBond(parentfragAtom, suffixAtom, bondToSuffix.getOrder());
                            state.fragManager.removeBond(bondToSuffix);
                            if (suffixValue.equals("aldehyde") || suffixValue.equals("al")){
                            	if("X".equals(suffixAtom.getFirstLocant())){//carbaldehyde
                            		suffixAtom.setProperty(Atom.ISALDEHYDE, true);
                            	}
                            	else{
                            		parentfragAtom.setProperty(Atom.ISALDEHYDE, true);
                            	}
                            }
                        }
                    }
                } else if (suffixRuleTagName.equals(SUFFIXRULES_CHANGECHARGE_EL)) {
            		if (idOnParentFragToUse != 0) {
            			int chargeChange = Integer.parseInt(suffixRuleTag.getAttributeValue(SUFFIXRULES_CHARGE_ATR));
            			int protonChange = Integer.parseInt(suffixRuleTag.getAttributeValue(SUFFIXRULES_PROTONS_ATR));
                		frag.getAtomByIDOrThrow(idOnParentFragToUse).addChargeAndProtons(chargeChange, protonChange);
            		}
            		else{
                        applyUnlocantedChargeModification(atomList, suffixRuleTag);
            		}
                } else if (suffixRuleTagName.equals(SUFFIXRULES_SETOUTATOM_EL)) {
                    int outValency = suffixRuleTag.getAttribute(SUFFIXRULES_OUTVALENCY_ATR) != null ? Integer.parseInt(suffixRuleTag.getAttributeValue(SUFFIXRULES_OUTVALENCY_ATR)) : 1;
                    if (suffix.getAttribute(SUFFIXPREFIX_ATR) == null) {
                        if (idOnParentFragToUse != 0) {
                            frag.addOutAtom(idOnParentFragToUse, outValency, true);
                        } else {
                            frag.addOutAtom(firstAtomID, outValency, false);
                        }
                    } else {//something like oyl on a ring, which means it is now carbonyl and the outAtom is on the suffix and not frag
                        if (suffixFrag == null) {
                            throw new StructureBuildingException("OPSIN bug: ordering of elements in suffixRules.xml wrong; setOutAtom found before addGroup");
                        }
                        Set<Bond> bonds = state.fragManager.getInterFragmentBonds(suffixFrag);
                        if (bonds.size() != 1) {
                            throw new StructureBuildingException("OPSIN bug: Wrong number of bonds between suffix and group");
                        }
                        for (Bond bond : bonds) {
                            if (bond.getFromAtom().getFrag() == suffixFrag) {
                                suffixFrag.addOutAtom(bond.getFromAtom(), outValency, true);
                            } else {
                                suffixFrag.addOutAtom(bond.getToAtom(), outValency, true);
                            }
                        }
                    }
                } else if (suffixRuleTagName.equals(SUFFIXRULES_ADDSUFFIXPREFIXIFNONEPRESENTANDCYCLIC_EL)) {
                    //already processed
                } else if (suffixRuleTagName.equals(SUFFIXRULES_ADDFUNCTIONALATOMSTOHYDROXYGROUPS_EL)) {
                    //already processed
                } else if (suffixRuleTagName.equals(SUFFIXRULES_CHARGEHYDROXYGROUPS_EL)) {
                    //already processed
                } else if (suffixRuleTagName.equals(SUFFIXRULES_REMOVEONEDOUBLEBONDEDOXYGEN_EL)) {
                    //already processed
                } else if (suffixRuleTagName.equals(SUFFIXRULES_CONVERTHYDROXYGROUPSTOOUTATOMS_EL)) {
                    //already processed
                } else {
                    throw new StructureBuildingException("Unknown suffix rule:" + suffixRuleTagName);
                }
            }

            if (suffixFrag != null) {//merge suffix frag and parent fragment
                state.fragManager.removeAtomAndAssociatedBonds(suffixFrag.getFirstAtom());//the dummy R atom
                Set<String> suffixLocants = new HashSet<String>(suffixFrag.getLocants());
                for (String suffixLocant : suffixLocants) {
					if (Character.isDigit(suffixLocant.charAt(0))){//check that numeric locants do not conflict with the parent fragment e.g. hydrazide 2' with biphenyl 2'
						if (frag.hasLocant(suffixLocant)){
							suffixFrag.getAtomByLocant(suffixLocant).removeLocant(suffixLocant);
						}
					}
				}
                state.fragManager.incorporateFragment(suffixFrag, frag);
            }
        }
	}


	/**
	 * Preference is given to mono cation/anions as they are expected to be more likely
	 * Additionally, Typically if a locant has not been specified then it was intended to refer to a nitrogen even if the nitrogen is not at locant 1 e.g. isoquinolinium
	 * Hence preference is given to nitrogen atoms and then to non carbon atoms
	 * @param atomList
	 * @param suffixRuleTag
	 * @throws StructureBuildingException
	 */
	private void applyUnlocantedChargeModification(List<Atom> atomList, Element suffixRuleTag) throws StructureBuildingException {
		int chargeChange = Integer.parseInt(suffixRuleTag.getAttributeValue(SUFFIXRULES_CHARGE_ATR));
		int protonChange = Integer.parseInt(suffixRuleTag.getAttributeValue(SUFFIXRULES_PROTONS_ATR));

	    Atom likelyAtom = null;
	    Atom possibleHeteroatom = null;
	    Atom possibleCarbonAtom = null;
	    Atom possibleDiOrHigherIon = null;
	    for (Atom a : atomList) {
	    	Integer[] stableValencies = ValencyChecker.getPossibleValencies(a.getElement(), a.getCharge() + chargeChange);
	        if (stableValencies == null) {//unstable valency so seems unlikely
	            continue;
	        }
	        String element = a.getElement();
	        int resultantExpectedValency = (a.getLambdaConventionValency() ==null ? ValencyChecker.getDefaultValency(element) :  a.getLambdaConventionValency()) + a.getProtonsExplicitlyAddedOrRemoved() + protonChange;
	        boolean matched = false;
	        for (Integer stableValency : stableValencies) {
				if (stableValency ==resultantExpectedValency){
					matched =true;
					break;
				}
			}
        	if (!matched){//unstable valency so seems unlikely
        		continue;
        	}
        	if (protonChange <0 && StructureBuildingMethods.calculateSubstitutableHydrogenAtoms(a)<=0){
        		continue;
        	}
        	if (Math.abs(a.getCharge())==0){
        		if (element.equals("N")){
        			likelyAtom = a;
        			break;
        		}
        		else if (possibleHeteroatom ==null && !element.equals("C")){
        			possibleHeteroatom= a;
        		}
        		else if (possibleCarbonAtom ==null){
	        		possibleCarbonAtom = a;
        		}
        	}
        	else if (possibleDiOrHigherIon ==null){
        		possibleDiOrHigherIon = a;
        	}
	    }
	    if (likelyAtom == null) {
	    	if (possibleHeteroatom !=null){
	    		likelyAtom = possibleHeteroatom;
	    	}
	    	else if (possibleCarbonAtom !=null){
	    		likelyAtom = possibleCarbonAtom;
	    	}
	    	else if (possibleDiOrHigherIon !=null){
	    		likelyAtom = possibleDiOrHigherIon;
	    	}
	    	else{
	    		likelyAtom = atomList.get(0);
	    	}
	    }
	    likelyAtom.addChargeAndProtons(chargeChange, protonChange);
	}

	/**
	 * Moves a multiplier out of a bracket if the bracket contains only one substituent
	 * e.g. (trimethyl) --> tri(methyl).
	 * The multiplier may have locants e.g. [N,N-bis(2-hydroxyethyl)]
	 * This is done because OPSIN has no idea what to do with (trimethyl) as there is nothing within the scope to substitute onto!
	 * @param brackets
	 */
	private void moveErroneouslyPositionedLocantsAndMultipliers(List<Element> brackets) {
		for (int i = brackets.size()-1; i >=0; i--) {
			Element bracket =brackets.get(i);
			Elements childElements = bracket.getChildElements();
			boolean hyphenPresent = false;
			int childCount = childElements.size();
			if (childCount==2){
				for (int j = childCount -1; j >=0; j--) {
					if (childElements.get(j).getLocalName().equals(HYPHEN_EL)){
						hyphenPresent=true;
					}
				}
			}
			if (childCount==1 || hyphenPresent && childCount==2){
				Elements substituentContent = childElements.get(0).getChildElements();
				if (substituentContent.size()>=2){
					Element locant =null;
					Element multiplier =null;
					Element possibleMultiplier = substituentContent.get(0);
					if (substituentContent.get(0).getLocalName().equals(LOCANT_EL)){//probably erroneous locant
						locant = substituentContent.get(0);
						possibleMultiplier = substituentContent.get(1);
					}
					if (possibleMultiplier.getLocalName().equals(MULTIPLIER_EL)){//erroneously placed multiplier present
						multiplier = possibleMultiplier;
					}
					if (locant!=null){
						if (multiplier==null || matchComma.split(locant.getValue()).length == Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR))){
							locant.detach();
							XOMTools.insertBefore(childElements.get(0), locant);
						}
						else{
							continue;
						}
					}
					if (multiplier !=null){
						multiplier.detach();
						XOMTools.insertBefore(childElements.get(0), multiplier);
					}
				}
			}
		}
	}


	/**
	 * Given the right most child of a word:
	 * Checks whether this is multiplied e.g. methylenedibenzene
	 * If it is then it checks for the presence of locants e.g. 4,4'-oxydibenzene which has been changed to oxy-4,4'-dibenzene
	 * An attribute called inLocants is then added that is either INLOCANTS_DEFAULT or a comma seperated list of locants
	 * @param state
	 * @param rightMostElement
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException
	 */
	private void assignLocantsToMultipliedRootIfPresent(BuildState state, Element rightMostElement) throws ComponentGenerationException, StructureBuildingException {
		Elements multipliers = rightMostElement.getChildElements(MULTIPLIER_EL);
		if(multipliers.size() == 1) {
			Element multiplier =multipliers.get(0);
			if (XOMTools.getPrevious(multiplier)==null){
				throw new StructureBuildingException("OPSIN bug: Unacceptable input to function");
			}
			List<Element> locants = XOMTools.getChildElementsWithTagName(rightMostElement, MULTIPLICATIVELOCANT_EL);
			if (locants.size()>1){
				throw new ComponentGenerationException("OPSIN bug: Only none or one multiplicative locant expected");
			}
			int multiVal = Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR));
			if (locants.size()==0){
				rightMostElement.addAttribute(new Attribute(INLOCANTS_ATR, INLOCANTS_DEFAULT));
			}
			else{
				Element locantEl = locants.get(0);
				String[] locantValues = matchComma.split(locantEl.getValue());
				if (locantValues.length == multiVal){
					rightMostElement.addAttribute(new Attribute(INLOCANTS_ATR, locantEl.getValue()));
					locantEl.detach();
				}
				else{
					throw new ComponentGenerationException("Mismatch between number of locants and number of roots");
				}
			}
		}
		else if (rightMostElement.getLocalName().equals(BRACKET_EL)){
			assignLocantsToMultipliedRootIfPresent(state, ((Element) rightMostElement.getChild(rightMostElement.getChildCount()-1)));
		}
	}


	/**
	 * Assigns locants and multipliers to substituents/brackets
	 * If both locants and multipliers are present a final check is done that the number of them agree.
	 * WordLevel multipliers are processed e.g. diethyl ethanoate
	 * Adding a locant to a root or any other group that cannot engage in substitive nomenclature will result in an exception being thrown
	 * An exception is made for cases where the locant could be referring to a position on another word
	 * @param state 
	 * @param subOrBracket
	 * @throws ComponentGenerationException
	 * @throws StructureBuildingException 
	 */
	private void assignLocantsAndMultipliers(BuildState state, Element subOrBracket) throws ComponentGenerationException, StructureBuildingException {
		List<Element> locants = XOMTools.getChildElementsWithTagName(subOrBracket, LOCANT_EL);
		int multiplier =1;
		List<Element> multipliers =  XOMTools.getChildElementsWithTagName(subOrBracket, MULTIPLIER_EL);
		Element parentElem =(Element)subOrBracket.getParent();
		boolean oneBelowWordLevel = parentElem.getLocalName().equals(WORD_EL);
		Element groupIfPresent = subOrBracket.getFirstChildElement(GROUP_EL);
		if (multipliers.size()>0){
			if (multipliers.size()>1){
				throw new ComponentGenerationException(subOrBracket.getLocalName() +" has multiple multipliers, unable to determine meaning!");
			}
			if (oneBelowWordLevel &&
					XOMTools.getNextSibling(subOrBracket) == null &&
					XOMTools.getPreviousSibling(subOrBracket) == null) {
				return;//word level multiplier
			}
			multiplier = Integer.parseInt(multipliers.get(0).getAttributeValue(VALUE_ATR));
			subOrBracket.addAttribute(new Attribute(MULTIPLIER_ATR, multipliers.get(0).getAttributeValue(VALUE_ATR)));
			//multiplier is INTENTIONALLY not detached. As brackets/subs are only multiplied later on it is neccesary at that stage to determine what elements (if any) are in front of the multiplier
			if (groupIfPresent !=null && PERHALOGENO_SUBTYPE_VAL.equals(groupIfPresent.getAttributeValue(SUBTYPE_ATR))){
				throw new StructureBuildingException(groupIfPresent.getValue() +" cannot be multiplied");
			}
		}
		if(locants.size() > 0) {
			if (multiplier==1 && oneBelowWordLevel){//locant might be word Level locant
				if (wordLevelLocantsAllowed(state, subOrBracket, locants.size())){//something like S-ethyl or S-(2-ethylphenyl) or S-4-tert-butylphenyl
					Element locant = locants.remove(0);
					if (matchComma.split(locant.getValue()).length!=1){
						throw new ComponentGenerationException("Multiplier and locant count failed to agree; All locants could not be assigned!");
					}
					parentElem.addAttribute(new Attribute(LOCANT_ATR, locant.getValue()));
					locant.detach();
					if (locants.size()==0){
						return;
					}
				}
			}
			if (subOrBracket.getLocalName().equals(ROOT_EL)){
				throw new ComponentGenerationException("Unable to assign all locants");
			}
			if (locants.size()!=1){
				throw new ComponentGenerationException("Unable to assign all locants");
			}
			Element locantEl = locants.get(0);
			String[] locantValues = matchComma.split(locantEl.getValue());
			if (multiplier != locantValues.length){
				throw new ComponentGenerationException("Multiplier and locant count failed to agree; All locants could not be assigned!");
			}

			Element parent =(Element) subOrBracket.getParent();
			//attempt to find cases where locant will not be utilised. This if statement allows the use of locants for ester formation
			if (!parent.getLocalName().equals(WORD_EL) || (parent.getAttributeValue(TYPE_ATR).equals(WordType.full.toString()) && !state.currentWordRule.equals(WordRule.carbonylDerivative))){
				Elements children =parent.getChildElements();
				boolean foundSomethingToSubstitute =false;
				for (int i = parent.indexOf(subOrBracket) +1 ; i < children.size(); i++) {
					if (!children.get(i).getLocalName().equals(HYPHEN_EL)){
						foundSomethingToSubstitute = true;
					}
				}
				if (!foundSomethingToSubstitute){
					throw new ComponentGenerationException("Unable to assign all locants");
				}
			}
			if (groupIfPresent !=null && PERHALOGENO_SUBTYPE_VAL.equals(groupIfPresent.getAttributeValue(SUBTYPE_ATR))){
				throw new StructureBuildingException(groupIfPresent.getValue() +" cannot be locanted");
			}
			subOrBracket.addAttribute(new Attribute(LOCANT_ATR, locantEl.getValue()));
			locantEl.detach();
		}
	}

	private boolean wordLevelLocantsAllowed(BuildState state, Element subOrBracket, int numberOflocants) {
		Element parentElem =(Element)subOrBracket.getParent();
		if (WordType.valueOf(parentElem.getAttributeValue(TYPE_ATR))==WordType.substituent
				&& (XOMTools.getNextSibling(subOrBracket)==null || numberOflocants>=2)){
			if (state.currentWordRule == WordRule.ester || state.currentWordRule == WordRule.functionalClassEster || state.currentWordRule == WordRule.multiEster || state.currentWordRule == WordRule.acetal){
				return true;
			}
		}
		if (state.currentWordRule == WordRule.biochemicalEster && parentElem.getLocalName().equals(WORD_EL)){
			Element wordRule = (Element) parentElem.getParent();
			Elements words = wordRule.getChildElements(WORD_EL);
			Element ateWord = words.get(words.size()-1);
			if (parentElem==ateWord){
				return true;
			}
		}
			
		return false;
	}


	/**
	 * If a word level multiplier is present e.g. diethyl butandioate then this is processed to ethyl ethyl butandioate
	 * If wordCount is 1 then an exception is thrown if a multiplier is encountered e.g. triphosgene parsed as tri phosgene
	 * @param state
	 * @param word
	 * @param wordCount 
	 * @throws StructureBuildingException
	 * @throws ComponentGenerationException
	 */
	private void processWordLevelMultiplierIfApplicable(BuildState state, Element word, int wordCount) throws StructureBuildingException, ComponentGenerationException {
		if (word.getChildCount()==1){
			Element subOrBracket = (Element) word.getChild(0);
			Element multiplier = subOrBracket.getFirstChildElement(MULTIPLIER_EL);
			if (multiplier !=null){
				int multiVal =Integer.parseInt(multiplier.getAttributeValue(VALUE_ATR));
				if (multiVal ==1){//mono
					return;
				}
				Elements locants =subOrBracket.getChildElements(LOCANT_EL);
				boolean assignLocants =false;
				boolean wordLevelLocants =false;
				if (XOMTools.getNextSibling(subOrBracket)==null && WordType.valueOf(word.getAttributeValue(TYPE_ATR))==WordType.substituent){//something like O,S-dimethyl phosphorothioate
					if (state.currentWordRule == WordRule.ester || state.currentWordRule == WordRule.functionalClassEster || state.currentWordRule == WordRule.multiEster || state.currentWordRule == WordRule.acetal){
						wordLevelLocants =true;
					}
				}
				if (locants.size()>1){
					throw new ComponentGenerationException("Unable to assign all locants");
				}
				String[] locantValues = null;
				if (locants.size()==1){
					locantValues = matchComma.split(locants.get(0).getValue());
					if (locantValues.length == multiVal){
						assignLocants=true;
						locants.get(0).detach();
						if (wordLevelLocants){
							word.addAttribute(new Attribute(LOCANT_ATR, locantValues[0]));
						}
						else{
							subOrBracket.addAttribute(new Attribute(LOCANT_ATR, locantValues[0]));
						}
					}
					else{
						throw new ComponentGenerationException("Unable to assign all locants");
					}
				}
				List<Element> elementsNotToBeMultiplied = new ArrayList<Element>();//anything before the multiplier
				for (int i = subOrBracket.indexOf(multiplier) -1 ; i >=0 ; i--) {
					Element el = (Element) subOrBracket.getChild(i);
					el.detach();
					elementsNotToBeMultiplied.add(el);
				}
				multiplier.detach();
				if (wordCount ==1){
					throw new StructureBuildingException("Unexpected multiplier found at start of word. Perhaps the name is trivial e.g. triphosgene");
				}
				for(int i=multiVal -1; i>=1; i--) {
					Element clone = state.fragManager.cloneElement(state, word);
					if (assignLocants){
						if (wordLevelLocants){
							clone.getAttribute(LOCANT_ATR).setValue(locantValues[i]);
						}
						else{
							((Element) clone.getChild(0)).getAttribute(LOCANT_ATR).setValue(locantValues[i]);
						}
					}
					XOMTools.insertAfter(word, clone);
				}
				for (Element el : elementsNotToBeMultiplied) {//re-add anything before multiplier to original word
					subOrBracket.insertChild(el, 0);
				}
			}
		}
	}
}
