package growthbook.sdk.java.testhelpers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

interface ITestCasesJsonHelper {
    JsonObject getTestCases();
    JsonArray evalConditionTestCases();
    JsonArray getHNVTestCases();
    JsonArray getBucketRangeTestCases();
    JsonArray featureTestCases();
    JsonArray runTestCases();
    JsonArray getChooseVariationTestCases();
    JsonArray getQueryStringOverrideTestCases();
    JsonArray getInNamespaceTestCases();
    JsonArray getEqualWeightsTestCases();
    JsonArray decryptionTestCases();
    JsonArray versionCompareTestCases_eq();
    JsonArray versionCompareTestCases_lt();
    JsonArray versionCompareTestCases_gt();
}
