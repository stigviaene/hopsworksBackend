package se.kth.meta.wscomm.message;

import java.util.List;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import se.kth.meta.entity.EntityIntf;
import se.kth.meta.entity.FieldPredefinedValues;
import se.kth.meta.entity.Fields;
import se.kth.meta.entity.Tables;
import se.kth.meta.entity.Templates;
import se.kth.meta.exception.ApplicationException;

/**
 *
 * @author vangelis
 */
public abstract class ContentMessage implements Message {

  protected int templateId;
  protected String action;
  protected Templates template;

  public void setTemplate(Templates template) {
    this.template = template;
  }

  public Templates getTemplate() throws ApplicationException {
    return this.template;
  }

  public void setTemplateid(int templateId) {
    this.templateId = templateId;
  }

  public int getTemplateid() {
    return this.templateId;
  }

  @Override
  public void setAction(String action) {
    this.action = action;
  }

  @Override
  public String getAction() {
    return this.action;
  }

  /**
   * Builds a JSON object that represents the front end board based on the
   * message action. It's the inverse of parseSchema()
   * <p>
   *
   * @param entities Entities is a list of Tables objects
   * @return the schema as a JSON string
   */
  @Override
  public String buildSchema(List<EntityIntf> entities) {

    switch (Command.valueOf(this.action.toUpperCase())) {
      case STORE_FIELD:
      case DELETE_FIELD:
      case DELETE_TABLE:
      case STORE_TEMPLATE:
      case EXTEND_TEMPLATE:
      case FETCH_TEMPLATE:
        return this.templateContents(entities);
      case ADD_NEW_TEMPLATE:
      case REMOVE_TEMPLATE:
      case FETCH_TEMPLATES:
        return this.templateNames(entities);
      default:
        return "Unknown command";
    }
  }

  private String templateNames(List<EntityIntf> entities) {
    JsonObjectBuilder builder = Json.createObjectBuilder();

    builder.add("numberOfTemplates", entities.size());

    JsonArrayBuilder templates = Json.createArrayBuilder();
    for (EntityIntf e : entities) {

      Templates te = (Templates) e;
      JsonObjectBuilder template = Json.createObjectBuilder();

      template.add("id", te.getId());
      template.add("name", te.getName());
//            System.out.println("TEMPLATE ID " + te.getId());
//            System.out.println("TEMPLATE NAME " + te.getName());
      templates.add(template);
    }

    builder.add("templates", templates);

    return builder.build().toString();
  }

  private String templateContents(List<EntityIntf> entities) {
    JsonObjectBuilder builder = Json.createObjectBuilder();

    builder.add("name", "MainBoard");
    builder.add("numberOfColumns", 3 /*
     * entities.size()
     */);

    JsonArrayBuilder columns = Json.createArrayBuilder();

    //create the columns/lists/tables of the board
    for (EntityIntf ta : entities) {

      Tables t = (Tables) ta;
      JsonObjectBuilder column = Json.createObjectBuilder();

      column.add("id", t.getId());
      column.add("name", t.getName());

      List<Fields> fields = t.getFields();
      //array holding the cards
      JsonArrayBuilder cards = Json.createArrayBuilder();

      //create the column cards
      for (Fields c : fields) {
        JsonObjectBuilder card = Json.createObjectBuilder();
        card.add("id", c.getId());
        card.add("title", c.getName());
        card.add("find", c.getSearchable());
        card.add("required", c.getRequired());
        card.add("description", c.getDescription());
        card.add("fieldtypeid", c.getFieldTypeId());

        //"fieldtypeContent":[{"id":-1,"fieldid":-1,"value":"cc"},{"id":-1,"fieldid":-1,"value":"ccc"},{"id":-1,"fieldid":-1,"value":"cccc"}]
        JsonObjectBuilder temp = Json.createObjectBuilder();
        temp.add("showing", false);
        temp.add("value", String.valueOf(c.getMaxsize()));
        card.add("sizefield", temp);

        JsonArrayBuilder arr = Json.createArrayBuilder();
        for (FieldPredefinedValues value : c.getFieldPredefinedValues()) {
          JsonObjectBuilder obj = Json.createObjectBuilder();
          obj.add("id", value.getId());
          obj.add("fieldid", value.getFieldid());
          obj.add("value", value.getValue());
          arr.add(obj);
        }

        card.add("fieldtypeContent", arr);
        cards.add(card);
      }
      //add the cards to the column
      column.add("cards", cards);
      //add the current column to columns array
      columns.add(column);
    }

    //last but not least
    builder.add("columns", columns);

    return builder.build().toString();
  }

}
