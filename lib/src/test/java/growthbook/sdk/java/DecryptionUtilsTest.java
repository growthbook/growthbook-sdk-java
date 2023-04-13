package growthbook.sdk.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DecryptionUtilsTest {
    final TestCasesJsonHelper helper = TestCasesJsonHelper.getInstance();

    @Test
    void test_canDecryptEncryptedPayload_1() {
        String payload = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";

        String expected = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"hola\"}]}}";

        String actual = DecryptionUtils.decrypt(payload, encryptionKey);

        assertEquals(expected, actual.trim());
    }

    @Test
    void test_canDecryptEncryptedPayload_2() {
        String payload = "t58u96klH/wWz7bDILc7Fg==.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";
        String encryptionKey = "5FVDjWF4ThInixRmwbMaLA==";

        String expected = "{\"testfeatureabc\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":\"123\"},\"force\":true},{\"condition\":{\"id\":\"123\"},\"force\":true},{\"condition\":{\"id\":\"123\"},\"force\":true},{\"force\":true},{\"condition\":{\"id\":\"test\"},\"force\":true}]},\"savedgroupfeature\":{\"defaultValue\":true,\"rules\":[{\"condition\":{\"id\":{\"$in\":[\"1\",\"2\",\"3\",\"4\"]}},\"force\":false}]},\"anotherfeatureusingsavedgroup\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":{\"$in\":[\"1\",\"2\",\"3\",\"4\"]}},\"force\":true}]},\"feature-with-test-saved-group\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":{\"$in\":[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\"]}},\"force\":true}]},\"testexperiment\":{\"defaultValue\":\"bar\",\"rules\":[{\"variations\":[\"bar\",\"foo\",\"foo\"],\"coverage\":1,\"weights\":[0.334,0.333,0.333],\"key\":\"testexperiment\",\"hashAttribute\":\"id\"}]},\"test\":{\"defaultValue\":true,\"rules\":[{\"condition\":{\"id\":\"1234\"},\"force\":false}]},\"bug-test\":{\"defaultValue\":true},\"experiment-with-variations\":{\"defaultValue\":0,\"rules\":[{\"condition\":{\"id\":\"test\"},\"force\":1},{\"variations\":[1,1,0],\"coverage\":1,\"weights\":[0.333,0.333,0.333],\"hashAttribute\":\"id\"},{\"variations\":[1,0,1,1,1],\"coverage\":1,\"weights\":[0.2,0.2,0.2,0.2,0.2],\"hashAttribute\":\"id\"},{\"variations\":[1,0,1,1],\"coverage\":1,\"weights\":[0.25,0.25,0.25,0.25],\"hashAttribute\":\"id\"},{\"variations\":[0,1,1,1,1],\"coverage\":1,\"weights\":[0.2,0.2,0.2,0.2,0.2],\"hashAttribute\":\"id\"}]},\"test-experiment\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1],\"coverage\":1,\"weights\":[0.5,0.5],\"key\":\"test-experiment\",\"hashAttribute\":\"id\"}]},\"test-experiement-2\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1],\"coverage\":1,\"weights\":[0.5,0.5],\"key\":\"test-experiement-2\",\"hashAttribute\":\"id\"}]},\"test-with-stashed-changes\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,1],\"coverage\":1,\"weights\":[0.333,0.333,0.333],\"key\":\"test-with-stashed-changes\",\"hashAttribute\":\"id\"}]},\"test-123\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,1,1],\"coverage\":1,\"weights\":[0.25,0.25,0.25,0.25],\"key\":\"test-123\",\"hashAttribute\":\"id\"}]},\"test-with-usememo\":{\"defaultValue\":0,\"rules\":[{\"variations\":[1,0,0,1],\"coverage\":1,\"weights\":[0.25,0.25,0.25,0.25],\"key\":\"test-with-usememo\",\"hashAttribute\":\"id\"}]},\"test-from-scratch\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,1],\"coverage\":1,\"weights\":[0.333,0.333,0.333],\"key\":\"test-from-scratch\",\"hashAttribute\":\"id\"}]},\"test1234\":{\"defaultValue\":0,\"rules\":[{\"variations\":[0,1,1,1],\"coverage\":1,\"weights\":[0.25,0.25,0.25,0.25],\"key\":\"test1234\",\"hashAttribute\":\"id\"}]},\"my-feature\":{\"defaultValue\":true},\"test-with-changes\":{\"defaultValue\":{},\"rules\":[{\"variations\":[{},{},{}],\"coverage\":1,\"weights\":[0.333,0.333,0.333],\"key\":\"test-with-changes\",\"hashAttribute\":\"id\"}]},\"test-with-adnan\":{\"defaultValue\":false,\"rules\":[{\"variations\":[false,true],\"coverage\":1,\"weights\":[0.5,0.5],\"key\":\"test-with-adnan\",\"hashAttribute\":\"id\"}]}}";

        String actual = DecryptionUtils.decrypt(payload, encryptionKey);

        assertEquals(expected, actual.trim());
    }

    @Test
    void test_canDecryptEncryptedPayload_3() {
        // URL: http://localhost:3100/api/features/sdk-7MfWjn4Uuawuaetu
        String payload = "jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET";
        String encryptionKey = "o0maZL/O7AphxcbRvaJIzw==";

        String expected = "{\"targeted_percentage_rollout\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":\"foo\"},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"test_feature\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"id\":{\"$not\":{\"$regex\":\"foo\"},\"$eq\":\"\"}},\"force\":true}]},\"sample_json\":{\"defaultValue\":{}},\"string_feature\":{\"defaultValue\":\"hello, world!\"},\"some_test_feature\":{\"defaultValue\":true},\"my_new_feature_jan17_5\":{\"defaultValue\":true},\"my_new_feature_jan17_13\":{\"defaultValue\":true}}";
        String actual = DecryptionUtils.decrypt(payload, encryptionKey);

        assertEquals(expected, actual.trim());
    }

    @Test
    void test_returnsNull_WhenPayloadInvalid() {
        String payload = "foobar";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";

        String result = DecryptionUtils.decrypt(payload, encryptionKey);

        assertNull(result);
    }

    @Test
    void test_returnsNull_WhenPayloadInvalid_decodingIv() {
        String payload = "foobar.bar";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";

        String result = DecryptionUtils.decrypt(payload, encryptionKey);

        assertNull(result);
    }

    @Test
    void test_returnsNull_WhenEncryptionKeyInvalid() {
        String payload = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        String encryptionKey = "foobar";

        String result = DecryptionUtils.decrypt(payload, encryptionKey);

        assertNull(result);
    }

    @Test
    void jsonTestCases_decrypt() {
        JsonArray testCases = helper.decryptionTestCases();

        ArrayList<String> passedTests = new ArrayList<>();
        ArrayList<String> failedTests = new ArrayList<>();
        ArrayList<Integer> failingIndexes = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            JsonArray test = (JsonArray) testCases.get(i);
            String name = test.get(0).getAsString();
            String payload = test.get(1).getAsString();
            String key = test.get(2).getAsString();

            JsonElement expectedElem = test.get(3);
            if (expectedElem.isJsonNull()) {
                // Null means no features can be parsed
                Exception exception = assertThrows(IllegalArgumentException.class, () -> {
                    DecryptionUtils.decrypt(payload, key);
                });
            } else {
                String expected = test.get(3).getAsString();

                String actual = DecryptionUtils.decrypt(payload, key).trim();

                assertEquals(expected, actual);
            }
        }
    }
}
