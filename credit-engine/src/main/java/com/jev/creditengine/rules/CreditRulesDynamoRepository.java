package com.jev.creditengine.rules;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;

@Component
public class CreditRulesDynamoRepository {

    private static final String TABLE_NAME = "CreditRules";
    private final DynamoDbClient client;
    private final DynamoDbTable<CreditRule> table;

    public CreditRulesDynamoRepository(DynamoDbClient client, DynamoDbEnhancedClient enhanced) {
        this.client = client;
        this.table = enhanced.table(TABLE_NAME, TableSchema.fromBean(CreditRule.class));
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        try {
            client.createTable(CreateTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .keySchema(
                            KeySchemaElement.builder().attributeName("ruleCategory").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("ruleId").keyType(KeyType.RANGE).build())
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("ruleCategory").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("ruleId").attributeType(ScalarAttributeType.S).build())
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L).writeCapacityUnits(5L).build())
                    .build());
        } catch (ResourceInUseException e) {
            // table already exists
        }
    }

    public List<CreditRule> findAll() {
        return table.scan().items().stream().toList();
    }

    public void save(CreditRule rule) {
        table.putItem(rule);
    }

    public boolean isEmpty() {
        return findAll().isEmpty();
    }
}
