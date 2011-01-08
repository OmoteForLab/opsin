package uk.ac.cam.ch.wwmm.opsin;

import static junit.framework.Assert.assertEquals;
import static uk.ac.cam.ch.wwmm.opsin.XmlDeclarations.*;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Elements;

import org.junit.Test;

public class ComponentGeneration_StereochemistryTest {

	@Test
	public void testUnlocantedS() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(S)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(1, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals(null, newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("S", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testMultipleUnLocanted() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(R,R)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(2, children.size());
		Element newStereochemistryEl1 = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl1.getLocalName());
		assertEquals(null, newStereochemistryEl1.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl1.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl1.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl2 = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl2.getLocalName());
		assertEquals(null, newStereochemistryEl2.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl2.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl2.getAttributeValue(TYPE_ATR));
	}

	@Test
	public void testLocantedR() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(1R)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(1, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("1", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testMultipleRorSLocanted() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(alphaR,3S,7'S)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(3, children.size());
		Element newStereochemistryEl1 = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl1.getLocalName());
		assertEquals("alpha", newStereochemistryEl1.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl1.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl1.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl2 = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl2.getLocalName());
		assertEquals("3", newStereochemistryEl2.getAttributeValue(LOCANT_ATR));
		assertEquals("S", newStereochemistryEl2.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl2.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl3 = children.get(2);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl3.getLocalName());
		assertEquals("7'", newStereochemistryEl3.getAttributeValue(LOCANT_ATR));
		assertEquals("S", newStereochemistryEl3.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl3.getAttributeValue(TYPE_ATR));
	}
	
	
	@Test
	public void testUnLocantedE() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(E)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(1, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals(null, newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("E", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(E_OR_Z_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testLocantedZ() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(5Z)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(1, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("5", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("Z", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(E_OR_Z_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testMultipleRorSorEorZ() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(NZ,2E,R)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(3, children.size());
		Element newStereochemistryEl1 = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl1.getLocalName());
		assertEquals("N", newStereochemistryEl1.getAttributeValue(LOCANT_ATR));
		assertEquals("Z", newStereochemistryEl1.getAttributeValue(VALUE_ATR));
		assertEquals(E_OR_Z_TYPE_VAL, newStereochemistryEl1.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl2 = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl2.getLocalName());
		assertEquals("2", newStereochemistryEl2.getAttributeValue(LOCANT_ATR));
		assertEquals("E", newStereochemistryEl2.getAttributeValue(VALUE_ATR));
		assertEquals(E_OR_Z_TYPE_VAL, newStereochemistryEl2.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl3 = children.get(2);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl3.getLocalName());
		assertEquals(null, newStereochemistryEl3.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl3.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl3.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testLocantedCisTrans() throws ComponentGenerationException {
		//XML for 3-cis,5-trans:
		Element substituent = new Element(SUBSTITUENT_EL);
		Element locant = new Element(LOCANT_ATR);
		locant.appendChild("3");
		substituent.appendChild(locant);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, CISORTRANS_TYPE_VAL));
		stereochem.addAttribute(new Attribute(VALUE_ATR, "cis"));
		stereochem.appendChild("cis");
		substituent.appendChild(stereochem);
		locant = new Element(LOCANT_ATR);
		locant.appendChild("5");
		substituent.appendChild(locant);
		stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, CISORTRANS_TYPE_VAL));
		stereochem.addAttribute(new Attribute(VALUE_ATR, "trans"));
		stereochem.appendChild("trans");
		substituent.appendChild(stereochem);
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(2, children.size());
		Element modifiedStereochemistryEl1 = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, modifiedStereochemistryEl1.getLocalName());
		assertEquals("3", modifiedStereochemistryEl1.getAttributeValue(LOCANT_ATR));
		assertEquals("cis", modifiedStereochemistryEl1.getAttributeValue(VALUE_ATR));
		assertEquals(CISORTRANS_TYPE_VAL, modifiedStereochemistryEl1.getAttributeValue(TYPE_ATR));
		
		Element modifiedStereochemistryEl2 = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, modifiedStereochemistryEl2.getLocalName());
		assertEquals("5", modifiedStereochemistryEl2.getAttributeValue(LOCANT_ATR));
		assertEquals("trans", modifiedStereochemistryEl2.getAttributeValue(VALUE_ATR));
		assertEquals(CISORTRANS_TYPE_VAL, modifiedStereochemistryEl2.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testAlphaBeta() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(1a,2b,3bEtA,4alpha,5xi)");
		Element naturalProduct = new Element(GROUP_EL);
		naturalProduct.addAttribute(new Attribute(SUBTYPE_ATR, NATURALPRODUCT_SUBTYPE_VAL));
		substituent.appendChild(naturalProduct);
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(6, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("1", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("alpha", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		newStereochemistryEl = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("2", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("beta", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		newStereochemistryEl = children.get(2);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("3", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("beta", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		newStereochemistryEl = children.get(3);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("4", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("alpha", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		newStereochemistryEl = children.get(4);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("5", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("xi", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
	}
	
	
	@Test
	public void testUnbracketedAlphaBeta() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("3beta,5alpha");
		Element naturalProduct = new Element(GROUP_EL);
		naturalProduct.addAttribute(new Attribute(SUBTYPE_ATR, NATURALPRODUCT_SUBTYPE_VAL));
		substituent.appendChild(naturalProduct);
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(3, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("3", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("beta", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		newStereochemistryEl = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("5", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("alpha", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testAlphaBetaNotNextToANaturalProduct1() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("3beta,5alpha");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(3, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("3", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("beta", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		newStereochemistryEl = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("5", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("alpha", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		Element newLocantEl = children.get(2);
		assertEquals(LOCANT_EL, newLocantEl.getLocalName());
		assertEquals("3,5", newLocantEl.getValue());
	}
	
	@Test
	public void testAlphaBetaNotNextToANaturalProduct2() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(3beta,5alpha)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(2, children.size());
		Element newStereochemistryEl = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("3", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("beta", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
		
		newStereochemistryEl = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl.getLocalName());
		assertEquals("5", newStereochemistryEl.getAttributeValue(LOCANT_ATR));
		assertEquals("alpha", newStereochemistryEl.getAttributeValue(VALUE_ATR));
		assertEquals(ALPHA_OR_BETA_TYPE_VAL, newStereochemistryEl.getAttributeValue(TYPE_ATR));
	}
	
	//relative stereochemistry is currently treated the same as absolute stereochemistry
	@Test
	public void testRelativeStereoChemistry1() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("rel-(1R,3S,4S,7R)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(4, children.size());
		Element newStereochemistryEl1 = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl1.getLocalName());
		assertEquals("1", newStereochemistryEl1.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl1.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl1.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl2 = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl2.getLocalName());
		assertEquals("3", newStereochemistryEl2.getAttributeValue(LOCANT_ATR));
		assertEquals("S", newStereochemistryEl2.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl2.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl3 = children.get(2);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl3.getLocalName());
		assertEquals("4", newStereochemistryEl3.getAttributeValue(LOCANT_ATR));
		assertEquals("S", newStereochemistryEl3.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl3.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl4 = children.get(3);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl4.getLocalName());
		assertEquals("7", newStereochemistryEl4.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl4.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl4.getAttributeValue(TYPE_ATR));
	}
	
	@Test
	public void testRelativeStereoChemistry2() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(1R*,3S*,4S*,7R*)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(4, children.size());
		Element newStereochemistryEl1 = children.get(0);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl1.getLocalName());
		assertEquals("1", newStereochemistryEl1.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl1.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl1.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl2 = children.get(1);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl2.getLocalName());
		assertEquals("3", newStereochemistryEl2.getAttributeValue(LOCANT_ATR));
		assertEquals("S", newStereochemistryEl2.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl2.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl3 = children.get(2);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl3.getLocalName());
		assertEquals("4", newStereochemistryEl3.getAttributeValue(LOCANT_ATR));
		assertEquals("S", newStereochemistryEl3.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl3.getAttributeValue(TYPE_ATR));
		
		Element newStereochemistryEl4 = children.get(3);
		assertEquals(STEREOCHEMISTRY_EL, newStereochemistryEl4.getLocalName());
		assertEquals("7", newStereochemistryEl4.getAttributeValue(LOCANT_ATR));
		assertEquals("R", newStereochemistryEl4.getAttributeValue(VALUE_ATR));
		assertEquals(R_OR_S_TYPE_VAL, newStereochemistryEl4.getAttributeValue(TYPE_ATR));
	}
	
	//racemates are currently treated identically to completely undefined
	@Test
	public void testRacemate1() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("rac-(2R)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(0, children.size());
	}
	
	@Test
	public void testRacemate2() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(RS)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(0, children.size());
	}
	
	@Test
	public void testRacemate3() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(RS)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(0, children.size());
	}

	@Test
	public void testRacemate4() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("rac-(2R,4R)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(0, children.size());
	}
	
	@Test
	public void testRacemate5() throws ComponentGenerationException {
		Element substituent = new Element(SUBSTITUENT_EL);
		Element stereochem = new Element(STEREOCHEMISTRY_EL);
		stereochem.addAttribute(new Attribute(TYPE_ATR, STEREOCHEMISTRYBRACKET_TYPE_VAL));
		substituent.appendChild(stereochem);
		stereochem.appendChild("(2RS,4RS)");
		ComponentGenerator.processStereochemistry(substituent);

		Elements children = substituent.getChildElements();
		assertEquals(0, children.size());
	}
}