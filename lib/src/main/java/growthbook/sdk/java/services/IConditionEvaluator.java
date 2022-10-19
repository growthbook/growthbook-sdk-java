package growthbook.sdk.java.services;

public interface IConditionEvaluator {
    Boolean evaluateCondition(String attributesJsonString, String conditionJsonString);
}
