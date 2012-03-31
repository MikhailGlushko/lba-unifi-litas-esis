package lt.itdbaltics.unifi.gateway.pain;

import iso.std.iso._20022.tech.xsd.pain_001_001.AccountIdentification4Choice;
import iso.std.iso._20022.tech.xsd.pain_001_001.ActiveOrHistoricCurrencyAndAmount;
import iso.std.iso._20022.tech.xsd.pain_001_001.AmountType3Choice;
import iso.std.iso._20022.tech.xsd.pain_001_001.BranchAndFinancialInstitutionIdentification4;
import iso.std.iso._20022.tech.xsd.pain_001_001.CashAccount16;
import iso.std.iso._20022.tech.xsd.pain_001_001.CreditTransferTransactionInformation10;
import iso.std.iso._20022.tech.xsd.pain_001_001.CustomerCreditTransferInitiationV03;
import iso.std.iso._20022.tech.xsd.pain_001_001.FinancialInstitutionIdentification7;
import iso.std.iso._20022.tech.xsd.pain_001_001.GenericOrganisationIdentification1;
import iso.std.iso._20022.tech.xsd.pain_001_001.GenericPersonIdentification1;
import iso.std.iso._20022.tech.xsd.pain_001_001.GroupHeader32;
import iso.std.iso._20022.tech.xsd.pain_001_001.OrganisationIdentification4;
import iso.std.iso._20022.tech.xsd.pain_001_001.OrganisationIdentificationSchemeName1Choice;
import iso.std.iso._20022.tech.xsd.pain_001_001.Party6Choice;
import iso.std.iso._20022.tech.xsd.pain_001_001.PartyIdentification32;
import iso.std.iso._20022.tech.xsd.pain_001_001.PaymentIdentification1;
import iso.std.iso._20022.tech.xsd.pain_001_001.PaymentInstructionInformation3;
import iso.std.iso._20022.tech.xsd.pain_001_001.PaymentMethod3Code;
import iso.std.iso._20022.tech.xsd.pain_001_001.PaymentTypeInformation19;
import iso.std.iso._20022.tech.xsd.pain_001_001.PersonIdentification5;
import iso.std.iso._20022.tech.xsd.pain_001_001.PersonIdentificationSchemeName1Choice;
import iso.std.iso._20022.tech.xsd.pain_001_001.Purpose2Choice;
import iso.std.iso._20022.tech.xsd.pain_001_001.RemittanceInformation5;
import iso.std.iso._20022.tech.xsd.pain_001_001.ServiceLevel8Choice;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

import lt.lba.xmlns._2011._11.unifi.customercredittransferinitiation.v03.ObjectFactory;
import lt.lba.xmlns._2011._11.unifi.customercredittransferinitiation.v03.SignableDocumentType;

public class CustomerCreditTransferConverter {
	private static final String SVCLVL_CODE_URGENT = "URGP";
	private static final String SVCLVL_CODE_NONURGENT = "NURG";

	private static final String PERSON_ID_CODE_CUST = "CUST";
	private static final String ULTIMATE_PARTY_ID_CODE_IBAN = "IBAN";
	private static final String ESIS_DATE_FORMAT = "yyyyMMdd";

	public static void convertMokesisToCustomerCreditTransfer(List<Map<String, Object>> rows, OutputStream stream) {
		
		XMLGregorianCalendar createDate = createXMLGregorianCalendar(new Date());

		GroupHeader32 header = new GroupHeader32();
		header.setMsgId(UUID.randomUUID().toString().replaceAll("-", ""));
		header.setCreDtTm(createDate);
		header.setNbOfTxs(Integer.toString(rows.size()));

		CustomerCreditTransferInitiationV03 credit = new CustomerCreditTransferInitiationV03();
		credit.setGrpHdr(header);

		Iterator<Map<String, Object>> it = rows.iterator();

		while (it.hasNext()) {
			Map<String, Object> row = it.next();

			PaymentTypeInformation19 type = new PaymentTypeInformation19();
			ServiceLevel8Choice svcLvl = new ServiceLevel8Choice();

			int priority = Integer.valueOf((String) row.get(Columns.PmtTpInfInstrPrty));
			if (priority == 1) {
				svcLvl.setCd(SVCLVL_CODE_NONURGENT);
			} else if (priority == 2) {
				svcLvl.setCd(SVCLVL_CODE_URGENT);
			}
			type.setSvcLvl(svcLvl);

			// -- create new transfer payment instruction
			PaymentInstructionInformation3 instr = new PaymentInstructionInformation3();
			instr.setPmtInfId(UUID.randomUUID().toString().replaceAll("-", ""));
			instr.setPmtMtd(PaymentMethod3Code.TRF);
			instr.setPmtTpInf(type);

			// -- set requested execution date
			String reqdExctnDtStr = (String) row.get(Columns.ReqdExctnDt);
			if (hasValue(reqdExctnDtStr)) {
				instr.setReqdExctnDt(getReqdExctnDt(reqdExctnDtStr));
			}

			// -- set debtor account
			AccountIdentification4Choice dbtrAcctIdIBAN = new AccountIdentification4Choice();
			dbtrAcctIdIBAN.setIBAN((String) row.get(Columns.DbtrAcctIdIBAN));

			CashAccount16 dbtrAcct = new CashAccount16();
			dbtrAcct.setId(dbtrAcctIdIBAN);
			instr.setDbtrAcct(dbtrAcct);

			// -- set debtor
			PartyIdentification32 dbtr = createParty((String) row.get(Columns.DbtrNm), (String) row.get(Columns.DbtrIdOtherId), (String) row.get(Columns.DbtrIdOtherIdCUST),
					PERSON_ID_CODE_CUST);
			instr.setDbtr(dbtr);

			// -- set debtor agent
			String dbtrAgtFinInstnIdBICStr = (String) row.get(Columns.DbtrAgtFinInstnIdBIC);
			if (hasValue(dbtrAgtFinInstnIdBICStr)) {
				instr.setDbtrAgt(getFinInstnId(dbtrAgtFinInstnIdBICStr));
			}

			// -- set ultimate debtor
			PartyIdentification32 ultmtDbtr = createParty((String) row.get(Columns.UltmtDbtrNm), (String) row.get(Columns.UltmtDbtrIdOtherId),
					(String) row.get(Columns.UltmtDbtrIdOtherIdIBAN), ULTIMATE_PARTY_ID_CODE_IBAN);
			if (ultmtDbtr != null) {
				instr.setUltmtDbtr(ultmtDbtr);
			}

			// -- set creditor account
			AccountIdentification4Choice cdtrAcctIdIBAN = new AccountIdentification4Choice();
			cdtrAcctIdIBAN.setIBAN((String) row.get(Columns.CdtrAcctIdIBAN));

			CreditTransferTransactionInformation10 cdtTrfTxInf = new CreditTransferTransactionInformation10();

			// -- set credit transfer number
			String cdtTrfTxInfPmtIdStr = (String) row.get(Columns.CdtTrfTxInfPmtId);
			PaymentIdentification1 pmtId = new PaymentIdentification1();
			pmtId.setInstrId(cdtTrfTxInfPmtIdStr);
			pmtId.setEndToEndId(cdtTrfTxInfPmtIdStr);
			cdtTrfTxInf.setPmtId(pmtId);

			// -- set creditor
			PartyIdentification32 cdtr = createParty((String) row.get(Columns.CdtrNm), (String) row.get(Columns.CdtrIdOtherId), (String) row.get(Columns.CdtrIdOtherIdCUST),
					PERSON_ID_CODE_CUST);
			cdtTrfTxInf.setCdtr(cdtr);

			// -- set creditor agent
			String cdtrAgtFinInstnIdBICStr = (String) row.get(Columns.CdtrAgtFinInstnIdBIC);
			if (hasValue(cdtrAgtFinInstnIdBICStr)) {
				cdtTrfTxInf.setCdtrAgt(getFinInstnId(cdtrAgtFinInstnIdBICStr));
			}

			// -- set ultimate creditor
			PartyIdentification32 ultmtCdtr = createParty((String) row.get(Columns.UltmtCdtrNm), (String) row.get(Columns.UltmtCdtrIdOtherId),
					(String) row.get(Columns.UltmtCdtrIdOtherIdIBAN), ULTIMATE_PARTY_ID_CODE_IBAN);
			if (ultmtCdtr != null) {
				cdtTrfTxInf.setUltmtCdtr(ultmtCdtr);
			}

			// -- set amount
			AmountType3Choice amt = createAmount((String) row.get(Columns.AmtInstdAmt), (String) row.get(Columns.AmtInstdAmtCcy));
			cdtTrfTxInf.setAmt(amt);

			// -- set purpose code (purpose is not the same as remittance!
			// Remittance is to identify particular instruction relation to the
			// particular invoice, whenever purpose is a static value, defining
			// the category of the payment)
			String purpPrtryStr = (String) row.get(Columns.PurpPrtry);
			if (hasValue(purpPrtryStr)) {
				Purpose2Choice purp = new Purpose2Choice();
				purp.setPrtry(purpPrtryStr);

				cdtTrfTxInf.setPurp(purp);
			}

			// -- set unstructured remittance information
			RemittanceInformation5 rmtInf = createRmtInf((String) row.get(Columns.RmtInfUstrd));
			cdtTrfTxInf.setRmtInf(rmtInf);

			// -- add credit transfer transaction information (credit part) to
			// the instruction
			instr.getCdtTrfTxInf().add(cdtTrfTxInf);

			// -- add instruction to the list of instructions in the current
			// document
			credit.getPmtInf().add(instr);
		}

		iso.std.iso._20022.tech.xsd.pain_001_001.Document document = new iso.std.iso._20022.tech.xsd.pain_001_001.Document();
		document.setCstmrCdtTrfInitn(credit);

		SignableDocumentType signableType = new SignableDocumentType();
		signableType.setDocument(document);

		serializeResults(signableType, stream);
	}

	private static XMLGregorianCalendar createXMLGregorianCalendar(Date currentDateTime) {
		GregorianCalendar calendar = new GregorianCalendar();
		calendar.setTime(currentDateTime);
		
		XMLGregorianCalendar createDate;
		try {
			createDate = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
		} catch (DatatypeConfigurationException e) {
			throw new RuntimeException(e);
		}
		
		return createDate;
	}

	private static RemittanceInformation5 createRmtInf(String string) {
		RemittanceInformation5 rmtInf = new RemittanceInformation5();

		// -- maximum allowed length is 300, splitting the string into chunks of
		// 140 each
		if (string.length() > 140) {
			String s1 = string.substring(0, 140);
			rmtInf.getUstrd().add(s1);

			String s2 = string.substring(140);
			rmtInf.getUstrd().add(s2.substring(0, 140));

			if (s2.length() > 140) {
				String s3 = s2.substring(140);
				if (s3.length() > 140) {
					s3 = s3.substring(0, 140);
				}

				rmtInf.getUstrd().add(s3);
			}
		} else {
			rmtInf.getUstrd().add(string);
		}

		return rmtInf;
	}

	private static AmountType3Choice createAmount(String instdAmtStr, String instdAmtCcy) {
		String d = instdAmtStr.substring(0, instdAmtStr.length() - 2) + "." + instdAmtStr.substring(instdAmtStr.length() - 2);

		BigDecimal amount = BigDecimal.valueOf(Double.valueOf(d));

		AmountType3Choice amt = new AmountType3Choice();
		ActiveOrHistoricCurrencyAndAmount instdAmt = new ActiveOrHistoricCurrencyAndAmount();
		instdAmt.setValue(amount);
		instdAmt.setCcy(instdAmtCcy);

		amt.setInstdAmt(instdAmt);

		return amt;
	}

	private static PartyIdentification32 createParty(String partyNm, String partyMainId, String partyAdditionalId, String partyAdditionalIdCode) {
		PartyIdentification32 party = null;

		if (hasValue(partyNm) || hasValue(partyMainId) || hasValue(partyAdditionalId)) {
			party = new PartyIdentification32();

			if (hasValue(partyNm)) {
				party.setNm(partyNm);
			}

			Party6Choice partyId = createPartyId(partyMainId, partyAdditionalId, partyAdditionalIdCode);
			if (partyId != null) {
				party.setId(partyId);
			}
		}

		return party;
	}

	private static void serializeResults(SignableDocumentType signableType, OutputStream stream) {
		try {
			DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
			DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
			Document doc = docBuilder.newDocument();

			ObjectFactory of = new ObjectFactory();
			JAXBElement<SignableDocumentType> signable = of.createSignableDocument(signableType);
			signable.setValue(signableType);

			JAXBContext context = JAXBContext.newInstance(SignableDocumentType.class);
			Marshaller marshaller = context.createMarshaller();
			marshaller.marshal(signable, doc);

			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			StreamResult result = new StreamResult(stream);
			transformer.transform(new DOMSource(doc), result);
		} catch (ParserConfigurationException e) {
			throw new RuntimeException(e);
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		} catch (TransformerConfigurationException e) {
			throw new RuntimeException(e);
		} catch (TransformerFactoryConfigurationError e) {
			throw new RuntimeException(e);
		} catch (TransformerException e) {
			throw new RuntimeException(e);
		}
	}

	private static BranchAndFinancialInstitutionIdentification4 getFinInstnId(String finInstnIdBICStr) {
		BranchAndFinancialInstitutionIdentification4 finInstn = new BranchAndFinancialInstitutionIdentification4();
		FinancialInstitutionIdentification7 finInstnIdBIC = new FinancialInstitutionIdentification7();
		finInstnIdBIC.setBIC(finInstnIdBICStr);
		finInstn.setFinInstnId(finInstnIdBIC);

		return finInstn;
	}

	private static Party6Choice createPartyId(String partyIdOtherIdStr, String partyIdOtherIdAdditionalStr, String additionalSchmeNmCodeCdStr) {

		Party6Choice partyIdOtherId = null;

		if (hasValue(partyIdOtherIdStr) || hasValue(partyIdOtherIdAdditionalStr)) {
			partyIdOtherId = new Party6Choice();

			if (hasValue(partyIdOtherIdStr) && partyIdOtherIdStr.length() == 11) {
				PersonIdentification5 prvtId = new PersonIdentification5();
				GenericPersonIdentification1 otherId = new GenericPersonIdentification1();
				otherId.setId(partyIdOtherIdStr);

				prvtId.getOthr().add(otherId);

				if (hasValue(partyIdOtherIdAdditionalStr)) {
					GenericPersonIdentification1 otherCustId = getPersonAdditionalId(partyIdOtherIdAdditionalStr, additionalSchmeNmCodeCdStr);

					prvtId.getOthr().add(otherCustId);
				}

				partyIdOtherId.setPrvtId(prvtId);
			} else {
				OrganisationIdentification4 orgId = new OrganisationIdentification4();

				if (hasValue(partyIdOtherIdStr)) {
					GenericOrganisationIdentification1 otherId = new GenericOrganisationIdentification1();
					otherId.setId(partyIdOtherIdStr);

					orgId.getOthr().add(otherId);
				}

				if (hasValue(partyIdOtherIdAdditionalStr)) {
					GenericOrganisationIdentification1 otherCustId = getOrganisationCustId(partyIdOtherIdAdditionalStr, additionalSchmeNmCodeCdStr);

					orgId.getOthr().add(otherCustId);
				}

				partyIdOtherId.setOrgId(orgId);
			}
		}

		return partyIdOtherId;
	}

	private static  boolean hasValue(String str) {
		return str != null && !str.isEmpty();
	}

	private static GenericOrganisationIdentification1 getOrganisationCustId(String partyIdOtherIdCustStr, String schmeNmCodeCdStr) {
		GenericOrganisationIdentification1 otherCustId = new GenericOrganisationIdentification1();
		otherCustId.setId(partyIdOtherIdCustStr);

		OrganisationIdentificationSchemeName1Choice schmeNmCodeCd = new OrganisationIdentificationSchemeName1Choice();

		if (!isProprietaryCode(schmeNmCodeCdStr)) {
			schmeNmCodeCd.setCd(schmeNmCodeCdStr);
		} else {
			schmeNmCodeCd.setPrtry(schmeNmCodeCdStr);
		}

		otherCustId.setSchmeNm(schmeNmCodeCd);

		return otherCustId;
	}

	private static GenericPersonIdentification1 getPersonAdditionalId(String otherIdAdditionalStr, String schmeNmCodeCdStr) {
		GenericPersonIdentification1 otherAdditionalId = new GenericPersonIdentification1();
		otherAdditionalId.setId(otherIdAdditionalStr);

		PersonIdentificationSchemeName1Choice schmeNmCodeCd = new PersonIdentificationSchemeName1Choice();

		if (!isProprietaryCode(schmeNmCodeCdStr)) {
			schmeNmCodeCd.setCd(schmeNmCodeCdStr);
		} else {
			schmeNmCodeCd.setPrtry(schmeNmCodeCdStr);
		}

		otherAdditionalId.setSchmeNm(schmeNmCodeCd);

		return otherAdditionalId;
	}

	private static boolean isProprietaryCode(String schmeNmCodeCdStr) {
		// -- in the future we can create a list of used Codes from external
		// list and check against the list instead
		return !schmeNmCodeCdStr.equals(PERSON_ID_CODE_CUST);
	}

	private static XMLGregorianCalendar getReqdExctnDt(String reqdExctnDtStr) {
		XMLGregorianCalendar reqdExctnDt = null;
		try {
			DateFormat formatter = new SimpleDateFormat(ESIS_DATE_FORMAT);
			Date date = (Date) formatter.parse(reqdExctnDtStr);

			GregorianCalendar calendar = new GregorianCalendar();
			calendar.setTime(date);
			
			reqdExctnDt = DatatypeFactory.newInstance().newXMLGregorianCalendar(calendar);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		} catch (DatatypeConfigurationException e) {
			throw new RuntimeException(e);
		}
		
		return reqdExctnDt;
	}
}
