package com.devara.hollow;

import com.devara.hollow.model.UserAccount;
import com.devara.hollow.service.HollowConsumerService;
import com.devara.hollow.service.HollowProducerService;
import com.netflix.hollow.core.read.engine.HollowReadStateEngine;
import com.netflix.hollow.core.read.engine.HollowTypeReadState;
import com.netflix.hollow.core.schema.HollowObjectSchema;
import com.netflix.hollow.core.schema.HollowSchema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hollow")
public class HollowController {

    private final HollowProducerService producerService;
    private final HollowConsumerService consumerService;

    @Autowired
    public HollowController(HollowProducerService producerService, HollowConsumerService consumerService) {
        this.producerService = producerService;
        this.consumerService = consumerService;
    }

    /**
     * Publish sample data to Hollow.
     * This creates a new data cycle with user accounts.
     */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publishData(@RequestBody(required = false) List<UserAccount> users) {
        if (users == null || users.isEmpty()) {
            // Generate sample data if none provided
            users = generateSampleData();
        }

        producerService.runCycle(users);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Data published successfully");
        response.put("recordCount", users.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Refresh the consumer to get the latest data.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshConsumer() {
        consumerService.getConsumer().triggerRefresh();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Consumer refreshed successfully");
        response.put("currentVersion", consumerService.getConsumer().getCurrentVersionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Get the current state information.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> response = new HashMap<>();
        
        long version = consumerService.getConsumer().getCurrentVersionId();
        HollowReadStateEngine stateEngine = consumerService.getConsumer().getStateEngine();
        
        response.put("currentVersion", version);
        response.put("hasData", version != Long.MIN_VALUE);
        
        if (stateEngine != null) {
            List<String> types = new ArrayList<>();
            stateEngine.getTypeStates().forEach(typeState -> types.add(typeState.getSchema().getName()));
            response.put("types", types);
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Generate sample user data for demonstration.
     */
    private List<UserAccount> generateSampleData() {
        List<UserAccount> users = new ArrayList<>();
        users.add(new UserAccount(1, "alice", true));
        users.add(new UserAccount(2, "bob", true));
        users.add(new UserAccount(3, "charlie", false));
        users.add(new UserAccount(4, "diana", true));
        users.add(new UserAccount(5, "eve", false));
        return users;
    }

    /**
     * Browse data stored in Hollow.
     * Returns all records of a given type or all types if not specified.
     */
    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> browseData(@RequestParam(required = false) String type) {
        Map<String, Object> response = new HashMap<>();
        HollowReadStateEngine stateEngine = consumerService.getConsumer().getStateEngine();
        
        long version = consumerService.getConsumer().getCurrentVersionId();
        if (version == Long.MIN_VALUE || stateEngine == null) {
            response.put("message", "No data available. Please publish data first.");
            response.put("hasData", false);
            return ResponseEntity.ok(response);
        }

        response.put("currentVersion", version);
        response.put("hasData", true);

        Map<String, List<Map<String, Object>>> dataByType = new HashMap<>();
        
        for (HollowTypeReadState typeState : stateEngine.getTypeStates()) {
            String typeName = typeState.getSchema().getName();
            
            if (type != null && !type.equals(typeName)) {
                continue;
            }
            
            List<Map<String, Object>> records = new ArrayList<>();
            var populatedOrdinals = typeState.getPopulatedOrdinals();
            
            for (int ordinal = populatedOrdinals.nextSetBit(0); ordinal >= 0; ordinal = populatedOrdinals.nextSetBit(ordinal + 1)) {
                Map<String, Object> record = new HashMap<>();
                record.put("ordinal", ordinal);
                
                HollowSchema schema = typeState.getSchema();
                if (schema instanceof HollowObjectSchema objectSchema) {
                    for (int i = 0; i < objectSchema.numFields(); i++) {
                        String fieldName = objectSchema.getFieldName(i);
                        Object value = readFieldValue(stateEngine, typeName, ordinal, fieldName, objectSchema.getFieldType(i));
                        record.put(fieldName, value);
                    }
                }
                
                records.add(record);
            }
            
            dataByType.put(typeName, records);
        }
        
        response.put("data", dataByType);
        return ResponseEntity.ok(response);
    }
    
    private Object readFieldValue(HollowReadStateEngine stateEngine, String typeName, int ordinal, String fieldName, HollowObjectSchema.FieldType fieldType) {
        var typeState = stateEngine.getTypeState(typeName);
        if (typeState == null) return null;
        
        var dataAccess = stateEngine.getTypeDataAccess(typeName);
        if (dataAccess == null) return null;
        
        try {
            var objectAccess = (com.netflix.hollow.core.read.dataaccess.HollowObjectTypeDataAccess) dataAccess;
            int fieldIndex = ((HollowObjectSchema) typeState.getSchema()).getPosition(fieldName);
            
            return switch (fieldType) {
                case INT -> objectAccess.readInt(ordinal, fieldIndex);
                case LONG -> objectAccess.readLong(ordinal, fieldIndex);
                case BOOLEAN -> objectAccess.readBoolean(ordinal, fieldIndex);
                case DOUBLE -> objectAccess.readDouble(ordinal, fieldIndex);
                case FLOAT -> objectAccess.readFloat(ordinal, fieldIndex);
                case STRING -> objectAccess.readString(ordinal, fieldIndex);
                case REFERENCE -> {
                    // Resolve reference to String type
                    int refOrdinal = objectAccess.readOrdinal(ordinal, fieldIndex);
                    if (refOrdinal >= 0) {
                        HollowObjectSchema schema = (HollowObjectSchema) typeState.getSchema();
                        String refTypeName = schema.getReferencedType(fieldIndex);
                        if ("String".equals(refTypeName)) {
                            var stringAccess = (com.netflix.hollow.core.read.dataaccess.HollowObjectTypeDataAccess) 
                                stateEngine.getTypeDataAccess("String");
                            if (stringAccess != null) {
                                yield stringAccess.readString(refOrdinal, 0);
                            }
                        }
                        yield "ref:" + refTypeName + "[" + refOrdinal + "]";
                    }
                    yield null;
                }
                default -> "complex type";
            };
        } catch (Exception e) {
            return "error reading value: " + e.getMessage();
        }
    }
}
