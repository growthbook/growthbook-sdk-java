package growthbook.sdk.java.services;

interface IConditionEvaluator {
    Boolean evaluateCondition(String attributesJsonString, String conditionJsonString);
}
