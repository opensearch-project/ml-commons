package org.opensearch.ml.plugin;

import org.opensearch.common.inject.AbstractModule;
import org.opensearch.ml.sdkclient.XContentClient;
import org.opensearch.sdk.SdkClient;

public class SdkClientModule extends AbstractModule {

    @Override
    protected void configure() {
        // TODO use setting to switch this to different client
        bind(SdkClient.class).to(XContentClient.class);
    }

}
