package gov.uspto.patent.serialize;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;

import org.apache.commons.lang3.text.WordUtils;

import gov.uspto.patent.DateTextType;
import gov.uspto.patent.FreetextField;
import gov.uspto.patent.TextType;
import gov.uspto.patent.model.Citation;
import gov.uspto.patent.model.CitationType;
import gov.uspto.patent.model.Claim;
import gov.uspto.patent.model.DescSection;
import gov.uspto.patent.model.DescriptionSection;
import gov.uspto.patent.model.DocumentDate;
import gov.uspto.patent.model.DocumentId;
import gov.uspto.patent.model.NplCitation;
import gov.uspto.patent.model.PatCitation;
import gov.uspto.patent.model.Patent;
import gov.uspto.patent.model.entity.Agent;
import gov.uspto.patent.model.entity.Assignee;
import gov.uspto.patent.model.entity.Entity;
import gov.uspto.patent.model.entity.EntityType;
import gov.uspto.patent.model.entity.Examiner;
import gov.uspto.patent.model.entity.Inventor;
import gov.uspto.patent.model.entity.Name;
import gov.uspto.patent.model.entity.NameOrg;
import gov.uspto.patent.model.entity.NamePerson;

/**
 * Serialize Patent as Json, flat with multi-valued fields in an json array.
 * 
 * @author Brian G. Feldman (brian.feldman@uspto.gov)
 *
 */
public class JsonMapperFlat implements DocumentBuilder<Patent, String> {

    private boolean pretty;

    public JsonMapperFlat(boolean pretty) {
        this.pretty = pretty;
    }

    @Override
    public String build(Patent patent) throws IOException {
        JsonObject json = buildJson(patent);
        if (pretty) {
            return getPrettyPrint(json);
        } else {
            return json.toString();
        }
    }

    public JsonObject buildJson(Patent patent) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        builder.add("patentCorpus", patent.getPatentCorpus().toString());
        builder.add("documentId", patent.getDocumentId().toText()); // Patent ID or Public Application ID.

        builder.add("patentType", patent.getPatentType().toString());

        builder.add("applicationId", patent.getApplicationId() != null ? patent.getApplicationId().toText() : "");

        builder.add("relatedIds", mapDocIds(patent.getRelationIds()));

        // OtherIds contain [documentId, applicationId, relatedIds]
        builder.add("otherIds", mapDocIds(patent.getOtherIds()));

        if (patent.getDateProduced() != null) {
            builder.add("productionDateRaw", patent.getDateProduced().getDateText(DateTextType.RAW));
            builder.add("productionDateISO", patent.getDateProduced().getDateText(DateTextType.ISO));
        }

        if (patent.getDatePublished() != null) {
            builder.add("publishedDate", patent.getDatePublished().getDateText(DateTextType.RAW));
            builder.add("publishedDateIso", patent.getDatePublished().getDateText(DateTextType.ISO));
        }

        builder.add("agent", mapEntity(patent.getAgent(), EntityField.NAME));
        builder.add("agentLastName", mapEntity(patent.getAgent(), EntityField.FIRSTNAME));
        builder.add("agentFirstName", mapEntity(patent.getAgent(), EntityField.LASTNAME));
        builder.add("agentAddress", mapEntity(patent.getAgent(), EntityField.ADDRESS));
        builder.add("agentRepType", mapAgentRep(patent.getAgent()));

        
        builder.add("applicant", mapEntity(patent.getApplicants(), EntityField.NAME));
        builder.add("applicantLastName", mapEntity(patent.getApplicants(), EntityField.FIRSTNAME));
        builder.add("applicantFirstName", mapEntity(patent.getApplicants(), EntityField.LASTNAME));
        builder.add("applicantAddress", mapEntity(patent.getApplicants(), EntityField.ADDRESS));
        builder.add("applicantCity", mapEntity(patent.getApplicants(), EntityField.CITY));
        builder.add("applicantCountry", mapEntity(patent.getApplicants(), EntityField.COUNTRY));

        builder.add("inventor", mapEntity(patent.getInventors(), EntityField.NAME));
        builder.add("inventorLastName", mapEntity(patent.getInventors(), EntityField.FIRSTNAME));
        builder.add("inventorFirstName", mapEntity(patent.getInventors(), EntityField.LASTNAME));
        builder.add("inventorAddress", mapEntity(patent.getInventors(), EntityField.ADDRESS));
        builder.add("inventorCity", mapEntity(patent.getInventors(), EntityField.CITY));
        builder.add("inventorCountry", mapEntity(patent.getInventors(), EntityField.COUNTRY));
        builder.add("inventorNationality", mapInventor(patent.getInventors(), InventorField.NATIONALITY));
        builder.add("inventorResidency", mapInventor(patent.getInventors(), InventorField.RESIDENCE));

        builder.add("assignee", mapEntity(patent.getAssignee(), EntityField.NAME));
        builder.add("assigneeRoles", mapAssigneeRoles(patent.getAssignee()));
        builder.add("assigneeAddress", mapEntity(patent.getAssignee(), EntityField.ADDRESS));
        builder.add("assigneeCity", mapEntity(patent.getAssignee(), EntityField.CITY));
        builder.add("assigneeCountry", mapEntity(patent.getAssignee(), EntityField.COUNTRY));

        builder.add("examiner", mapEntity(patent.getExaminers(), EntityField.NAME));
        builder.add("examinerDepartment", mapExaminerDepartment(patent.getExaminers()));

        builder.add("title", valueOrEmpty(patent.getTitle()));

        mapFreetextField(patent.getAbstract(), "abstract", builder);

        builder.add("descFullRaw", patent.getDescription().getAllRawText());

        DescriptionSection descSection = patent.getDescription().getSection(DescSection.REL_APP_DESC);
        if (descSection != null) {
            mapFreetextField(descSection, "descRelApp", builder);
        }

        descSection = patent.getDescription().getSection(DescSection.DRAWING_DESC);
        if (descSection != null) {
            mapFreetextField(descSection, "descDraw", builder);
        }

        descSection = patent.getDescription().getSection(DescSection.BRIEF_SUMMARY);
        if (descSection != null) {
            mapFreetextField(descSection, "descBrief", builder);
        }

        descSection = patent.getDescription().getSection(DescSection.DETAILED_DESC);
        if (descSection != null) {
            mapFreetextField(descSection, "descDetailed", builder);
        }

        mapClaimText(patent.getClaims(), builder);

        builder.add("citationsExaminerNpl", mapCitations(patent.getCitations(), true, CitationType.NPLCIT));
        builder.add("citationsExaminerPat", mapCitations(patent.getCitations(), true, CitationType.PATCIT));

        builder.add("citationsApplicantNpl", mapCitations(patent.getCitations(), false, CitationType.NPLCIT));
        builder.add("citationsApplicantClientPat", mapCitations(patent.getCitations(), false, CitationType.PATCIT));

        return builder.build();
    }

    public String getPrettyPrint(JsonObject jsonObject) throws IOException {
        Map<String, Boolean> config = new HashMap<String, Boolean>();
        config.put(JsonGenerator.PRETTY_PRINTING, true);

        JsonWriterFactory writerFactory = Json.createWriterFactory(config);

        String output = null;
        try (StringWriter sw = new StringWriter(); JsonWriter jsonWriter = writerFactory.createWriter(sw)) {
            jsonWriter.writeObject(jsonObject);
            output = sw.toString();
        }

        return output;
    }

    private JsonArray mapExaminerDepartment(List<Examiner> examiners) {

        Set<String> depts = new HashSet<String>();
        for (Examiner examiner : examiners) {
            depts.add(examiner.getDepartment());
        }

        return mapStringCollection(depts);
    }

    private JsonArray mapAgentRep(List<Agent> agents){
        JsonArrayBuilder arBldr = Json.createArrayBuilder();
        for (Agent agent : agents) {
            arBldr.add(agent.getRepType().toString());
        }
        return arBldr.build();
    }

    private JsonArray mapInventor(List<Inventor> inventors, InventorField inventorField) {
        JsonArrayBuilder arBldr = Json.createArrayBuilder();

        for (Inventor inventor : inventors) {
            switch (inventorField) {
            case NATIONALITY:
                if (inventor.getNationality() != null) {
                    arBldr.add(inventor.getNationality().toString());
                }
                break;
            case RESIDENCE:
                if (inventor.getResidency() != null) {
                    arBldr.add(valueOrEmpty(inventor.getResidency()));
                }
                break;
            }
        }

        return arBldr.build();
    }

    private JsonArray mapAssigneeRoles(List<Assignee> assignees) {
        JsonArrayBuilder arBldr = Json.createArrayBuilder();

        for (Assignee assignee : assignees) {
            arBldr.add(valueOrEmpty(assignee.getRole()));
            //arBldr.add(valueOrEmpty(assignee.getRoleDesc())); // "roleDefinition", 
        }

        return arBldr.build();
    }

    private void mapFreetextField(FreetextField field, String fieldName, JsonObjectBuilder builder) {
        for (TextType textType : TextType.values()) {
            builder.add(fieldName + "" + WordUtils.capitalize(textType.name().toLowerCase()), field.getText(textType));
        }
    }

    private void mapClaimText(List<Claim> claimList, JsonObjectBuilder builder) {
        for (TextType textType : TextType.values()) {
            JsonArrayBuilder arBldr = Json.createArrayBuilder();
            for (Claim claim : claimList) {
                if (claim != null) {
                    arBldr.add(claim.getText(textType));
                }
            }
            builder.add("claim" + WordUtils.capitalize(textType.name().toLowerCase()), arBldr.build());
        }
    }

    private String valueOrEmpty(String value) {
        if (value == null) {
            return "";
        } else {
            return value;
        }
    }

    private String valueOrEmpty(Enum value) {
        if (value == null) {
            return "";
        } else {
            return value.toString();
        }
    }

    private JsonArray mapDate(DocumentDate date, DateTextType dateType) {
        JsonArrayBuilder arBldr = Json.createArrayBuilder();
        if (date != null) {
            arBldr.add(date.getDateText(dateType));
        }
        return arBldr.build();
    }

    private JsonArray mapEntity(List<? extends Entity> entities, EntityField entityField) {
        JsonArrayBuilder arBldr = Json.createArrayBuilder();

        for (Entity entity : entities) {
            switch (entityField) {
            case NAME:
                Name name = entity.getName();
                if (name instanceof NamePerson) {
                    arBldr.add(((NamePerson) name).getName());
                } else {
                    arBldr.add(((NameOrg) name).getName());
                }
                break;
            case FIRSTNAME:
                if (entity.getName() instanceof NamePerson) {
                    NamePerson name2 = (NamePerson) entity.getName();
                    if (name2.getFirstName() != null) {
                        arBldr.add(name2.getFirstName());
                    }
                }
                break;
            case LASTNAME:
                if (entity.getName() instanceof NamePerson) {
                    NamePerson name3 = (NamePerson) entity.getName();
                    if (name3.getLastName() != null) {
                        arBldr.add(name3.getLastName());
                    }
                }
                break;
            case ADDRESS:
                if (entity.getAddress() != null) {
                    arBldr.add(entity.getAddress().toText());
                }
                break;
            case COUNTRY:
                if (entity.getAddress() != null) {
                    arBldr.add(entity.getAddress().getCountry().toString());
                }
                break;
            case CITY:
                if (entity.getAddress() != null) {
                    arBldr.add(entity.getAddress().getCity());
                }
                break;
            }
        }

        return arBldr.build();
    }

    private JsonArray mapStringCollection(Collection<String> strings) {
        JsonArrayBuilder arBldr = Json.createArrayBuilder();
        if (strings != null) {
            for (String tok : strings) {
                arBldr.add(tok);
            }
        }
        return arBldr.build();
    }

    private JsonArray mapDocIds(List<DocumentId> docIds) {
        JsonArrayBuilder arBldr = Json.createArrayBuilder();
        if (docIds != null) {
            for (DocumentId docId : docIds) {
                if (docId != null) {
                    arBldr.add(docId.toText());
                }
            }
        }
        return arBldr.build();
    }

    private JsonArray mapCitations(List<Citation> CitationList, boolean examinerCited, CitationType citeType) {
        JsonArrayBuilder arBldr = Json.createArrayBuilder();

        for (Citation cite : CitationList) {
            if (cite.isExaminerCited() == examinerCited) {
                if (citeType == CitationType.NPLCIT && cite.getCitType() == CitationType.NPLCIT) {
                    NplCitation nplCite = (NplCitation) cite;
                    arBldr.add(nplCite.getCiteText());
                } else if (citeType == CitationType.PATCIT && cite.getCitType() == CitationType.PATCIT) {
                    PatCitation patCite = (PatCitation) cite;
                    arBldr.add(patCite.getDocumentId().toText());
                }
            }
        }

        return arBldr.build();
    }

    private enum EntityField {
        NAME, FIRSTNAME, LASTNAME, ADDRESS, COUNTRY, CITY
    }

    private enum InventorField {
        NATIONALITY, RESIDENCE
    }
}