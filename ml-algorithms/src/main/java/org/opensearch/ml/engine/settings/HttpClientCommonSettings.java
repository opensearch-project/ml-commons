package org.opensearch.ml.engine.settings;

import org.opensearch.common.settings.Setting;

public class HttpClientCommonSettings {

    public static final Setting<Integer> ML_COMMONS_HTTP_CLIENT_CONNECTION_TIMEOUT_IN_MILLI_SECOND =
        Setting.intSetting("plugins.ml_commons.http_client.connection_timeout.in_millisecond", 1000, 1, Setting.Property.NodeScope, Setting.Property.Final);

    public static final Setting<Integer> ML_COMMONS_HTTP_CLIENT_READ_TIMEOUT_IN_MILLI_SECOND =
        Setting.intSetting("plugins.ml_commons.http_client.read_timeout.in_millisecond", 3000, 1, Setting.Property.NodeScope, Setting.Property.Final);

    public static final Setting<Integer> ML_COMMONS_HTTP_CLIENT_MAX_TOTAL_CONNECTIONS =
        Setting.intSetting("plugins.ml_commons.http_client.max_total_connections", 20, 20, Setting.Property.NodeScope, Setting.Property.Final);

}
