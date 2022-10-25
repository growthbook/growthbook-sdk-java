package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Context;
import growthbook.sdk.java.models.Feature;
import growthbook.sdk.java.models.FeatureResult;
import growthbook.sdk.java.models.FeatureResultSource;

public class FeatureEvaluator implements IFeatureEvaluator {
    @Override
    public FeatureResult evaluateFeature(String key, Context context) {
        FeatureResult emptyFeature = FeatureResult
                .builder()
                .rawJsonValue(null)
                .on(false)
                .source(FeatureResultSource.UNKNOWN_FEATURE)
                .build();

        try {
            Feature feature = context.getFeatures().get(key);
            if (feature == null) {
                return emptyFeature;
            }

            // If empty rule set, use the default value
            if (feature.getRules().isEmpty()) {
                return FeatureResult
                        .builder()
                        .source(FeatureResultSource.DEFAULT_VALUE)
                        .rawJsonValue(feature.getDefaultValue())
                        .build();
            }

            // TODO: evaluate feature result

            return emptyFeature;

        } catch (Exception e) {
            e.printStackTrace();
            return emptyFeature;
        }
    }
}
