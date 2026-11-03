package org.opensearch.ml.common.utils.mergeMetaDataUtils;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.gson.Gson;

public class MergeRuleHelperTests {

    private Gson gson = new Gson();

    private Map<String, Object> prepareMap1() {
        String mapBlock = """
            {
                "event": {
                    "properties": {
                        "field1": {
                            "type": "string"
                        }
                    }
                }
            }

            """;
        Map<String, Object> tmpMap = gson.fromJson(mapBlock, Map.class);
        return tmpMap;
    }

    private Map<String, Object> prepareMap2() {
        String mapBlock = """
            {
                "event": {
                    "properties": {
                        "field2": {
                            "type": "string"
                        }
                    }
                }
            }

            """;
        Map<String, Object> tmpMap = gson.fromJson(mapBlock, Map.class);
        return tmpMap;
    }

    private Map<String, Object> prepareNormalMap1() {
        String mapBlock = """
            {
                "event1": {
                    "properties": {
                        "field1": {
                            "type": "string"
                        }
                    }
                },
                "replace" : {
                    "type":"string"
                }

            }

            """;
        Map<String, Object> tmpMap = gson.fromJson(mapBlock, Map.class);
        return tmpMap;
    }

    private Map<String, Object> prepareNormalMap2() {
        String mapBlock = """
            {
                "event2": {
                    "properties": {
                        "field2": {
                            "type": "string"
                        }
                    }
                },
                "replace" : {
                    "type":"keyword"
                }
            }

            """;
        Map<String, Object> tmpMap = gson.fromJson(mapBlock, Map.class);
        return tmpMap;
    }

    @Test
    public void testMergeTwoObjectMaps() {
        String mapBlock = """
            {
                "event": {
                    "properties": {
                        "field1": {
                            "type": "string"
                        },
                        "field2": {
                            "type": "string"
                        }
                    }
                }
            }

            """;
        Map<String, Object> allFields = new HashMap<>();
        Map<String, Object> map1 = prepareMap1();
        Map<String, Object> map2 = prepareMap2();
        MergeRuleHelper.merge(map1, allFields);
        MergeRuleHelper.merge(map2, allFields);
        assertEquals(allFields, gson.fromJson(mapBlock, Map.class));
    }

    @Test
    public void testMergeTwoNormalMaps() {
        String mapBlock = """
            {
                "event1": {
                    "properties": {
                        "field1": {
                            "type": "string"
                        }
                    }
                },
                "event2": {
                    "properties": {
                        "field2": {
                            "type": "string"
                        }
                    }
                },
                "replace" : {
                    "type":"keyword"
                }
            }

            """;
        Map<String, Object> allFields = new HashMap<>();
        Map<String, Object> map1 = prepareNormalMap1();
        Map<String, Object> map2 = prepareNormalMap2();
        MergeRuleHelper.merge(map1, allFields);
        MergeRuleHelper.merge(map2, allFields);
        assertEquals(allFields, gson.fromJson(mapBlock, Map.class));
    }

    @Test
    public void testMergeTwoDeepMaps() {
        String mapBlock = """
            {
                "event": {
                    "properties": {
                        "field1": {
                            "type": "string"
                        },
                        "field2": {
                            "type": "string"
                        },
                        "deep": {
                            "properties": {
                                "field1": {
                                    "type": "string"
                                },
                                "field2": {
                                    "type": "string"
                                }
                            }
                        }
                    }
                }

            }

            """;
        Map<String, Object> allFields = new HashMap<>();
        Map<String, Object> map1 = prepareDeepMap1();
        Map<String, Object> map2 = prepareDeepMap2();
        MergeRuleHelper.merge(map1, allFields);
        MergeRuleHelper.merge(map2, allFields);
        assertEquals(allFields, gson.fromJson(mapBlock, Map.class));
    }

    private Map<String, Object> prepareDeepMap1() {
        String mapBlock = """
            {
                "event": {
                    "properties": {
                        "field1": {
                            "type": "string"
                        },
                        "deep": {
                            "properties": {
                                "field1": {
                                    "type": "string"
                                }
                            }
                        }
                    }
                }

            }

            """;
        Map<String, Object> tmpMap = gson.fromJson(mapBlock, Map.class);
        return tmpMap;
    }

    private Map<String, Object> prepareDeepMap2() {
        String mapBlock = """
            {
                "event": {
                    "properties": {
                        "field2": {
                            "type": "string"
                        },
                        "deep": {
                            "properties": {
                                "field2": {
                                    "type": "string"
                                }
                            }
                        }
                    }
                }
            }

            """;
        Map<String, Object> tmpMap = gson.fromJson(mapBlock, Map.class);
        return tmpMap;
    }
}
