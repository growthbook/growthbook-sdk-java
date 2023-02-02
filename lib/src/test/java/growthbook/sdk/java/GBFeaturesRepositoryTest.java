package growthbook.sdk.java;

import okhttp3.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GBFeaturesRepositoryTest {

    @Test
    void canBeConstructed_withNullEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
            null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getEndpoint());
    }

    @Test
    void canBeConstructed_withEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD",
            "BhB1wORFmZLTDjbvstvS8w=="
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test
    void canBeBuilt_withNullEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
            .builder()
            .endpoint("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8")
            .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getEndpoint());
    }

    @Test
    void canBeBuilt_withEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
            .builder()
            .endpoint("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD")
            .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
            .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    /*
    @Test
    void canFetchUnencryptedFeatures_real() throws FeatureFetchException {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
            null
        );

        subject.initialize();

        assertEquals(
            "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}",
            subject.getFeaturesJson()
        );
    }
    */

    @Test
    void canFetchUnencryptedFeatures_mockedResponse() throws FeatureFetchException, IOException {
        String fakeResponseJson = "{\"status\":200,\"features\":{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}},\"dateUpdated\":\"2023-01-11T00:26:01.745Z\"}";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);

        GBFeaturesRepository subject = new GBFeaturesRepository(
            mockOkHttpClient,
            "http://localhost:80",
            null
        );
        subject.initialize();

        String expected = "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}";
        assertEquals(expected, subject.getFeaturesJson());
    }

    @Test
    void canFetchEncryptedFeatures_real() throws FeatureFetchException {
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "http://localhost:3100/api/features/sdk-7MfWjn4Uuawuaetu",
            encryptionKey
        );

        subject.initialize();

        assertEquals(
            "{\"banner_text\":{\"defaultValue\":\"Welcome to Acme Donuts!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue au Beignets Acme !\"},{\"condition\":{\"country\":\"spain\"},\"force\":\"¡Bienvenidos y bienvenidas a Donas Acme!\"}]},\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}}",
            subject.getFeaturesJson()
        );
    }

    @Test
    void canFetchEncryptedFeatures_mockedResponse() throws IOException, FeatureFetchException {
        String fakeResponseJson = "{\n" +
            "  \"status\": 200,\n" +
            "  \"features\": {},\n" +
            "  \"dateUpdated\": \"2023-01-25T00:51:26.772Z\",\n" +
            "  \"encryptedFeatures\": \"Im7+N+s8exPaS1/9vgQRvQ==.3QnDpYJWpRM3L1vNqnaXCIgaYnTXm0b6orNweuCXBsMqhDAETsRmLHUHC8Y8D12D4bCyNIsDUmQipOVjpmj8bJ5mqAyOvV7aTuwrF+F5kXRZufcl7lw3ra/9fI24KNGzLGKIS8mEPnP1+rV31tPl/6shV97LWUfJ4V0xKGEZhdHUhdhYo6U6iainGqxWPp+9tRffE3DQznsTDzz0tKyDZ0qDn+3ETylwsolk6W3sqgAPmMPM6KUjcQ0s3O0W7C+mS4N9M2ng75gwR9rPLKHv7qHh6uGKcpqx1dkWn5w4v7CzeRawfLsVEp8Z8Rb/NgYfSmUGA8ma8xn6YDFjLuIhvMy8uo4Tvk17kKt7WHHs7g7+fUe564ZV/jcLuXREmKgkG9frksZObvlu2YYcnpRxaRGWi8x5dJkHqn7BEAxetMWqZPrHv3HkQE5+Iw5B3EGblWTv1eBdcoOiDAIUf59EZ1/U0D9bmAKSpNyZpjtKUhiN1fUS2ikWo4Z3OxwfP46M8gFMH0wc9DVjTgxWRm+7mRuHuRnINr2HvnGDQ+bOTUlNkCZq435ur2EExdNB23jwMe2zSHaRJDEdOGxWkOB8/VggNWzEs90O8rql9tVfhztBmUdBQMY8e1IjO0O6VUHh\"\n" +
            "}";
        String encryptionKey = "BhB1wORFmZLTDjbvstvS8w==";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);

        GBFeaturesRepository subject = new GBFeaturesRepository(
            mockOkHttpClient,
            "http://localhost:80",
            encryptionKey
        );
        subject.initialize();

        String expected = "{\"greeting\":{\"defaultValue\":\"hello\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"bonjour\"},{\"condition\":{\"country\":\"mexico\"},\"force\":\"hola\"}]}}";
        assertEquals(expected, subject.getFeaturesJson());
    }

    /**
     * Create a mock instance of {@link OkHttpClient}
     * @param serializedBody JSON string response
     * @return mock {@link OkHttpClient}
     */
    private static OkHttpClient mockHttpClient(final String serializedBody) throws IOException {
        final OkHttpClient okHttpClient = mock(OkHttpClient.class);

        final Call remoteCall = mock(Call.class);

        final Response response = new Response.Builder()
            .request(new Request.Builder().url("http://url.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200).message("").body(
                ResponseBody.create(
                    serializedBody,
                    MediaType.parse("application/json")
                ))
            .build();

        when(remoteCall.execute()).thenReturn(response);
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);

        return okHttpClient;
    }
}
