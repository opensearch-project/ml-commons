package org.opensearch.ml.action.controller;

import static org.opensearch.ml.common.CommonValue.ML_MODEL_CONTROLLER_INDEX;
import static org.opensearch.ml.common.FunctionName.REMOTE;
import static org.opensearch.ml.common.FunctionName.TEXT_EMBEDDING;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.Client;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.commons.authuser.User;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.ml.common.FunctionName;
import org.opensearch.ml.common.MLModel;
import org.opensearch.ml.common.controller.MLModelController;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLDeployModelControllerNodesRequest;
import org.opensearch.ml.common.transport.controller.MLUpdateModelControllerAction;
import org.opensearch.ml.common.transport.controller.MLUpdateModelControllerRequest;
import org.opensearch.ml.helper.ModelAccessControlHelper;
import org.opensearch.ml.model.MLModelCacheHelper;
import org.opensearch.ml.model.MLModelManager;
import org.opensearch.ml.utils.RestActionUtils;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

public class UpdateModelControllerTransportActionTests extends OpenSearchTestCase {
    
}
