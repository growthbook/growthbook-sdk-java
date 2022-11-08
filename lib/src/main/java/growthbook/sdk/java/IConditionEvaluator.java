package growthbook.sdk.java;

interface IConditionEvaluator {
    Boolean evaluateCondition(String attributesJsonString, String conditionJsonString);
}
