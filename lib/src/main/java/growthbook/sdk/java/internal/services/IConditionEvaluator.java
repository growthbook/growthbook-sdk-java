package growthbook.sdk.java.internal.services;

interface IConditionEvaluator {
    Boolean evaluateCondition(String attributesJsonString, String conditionJsonString);
}
