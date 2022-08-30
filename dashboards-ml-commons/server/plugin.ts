import {
  PluginInitializerContext,
  CoreSetup,
  CoreStart,
  Plugin,
  Logger,
} from '../../../../src/core/server';

import createTrainCluster from './clusters/create_train_cluster';
import createModelCluster from './clusters/create_model_cluster';
import createTaskCluster from './clusters/create_task_cluster';
import { MlCommonsPluginSetup, MlCommonsPluginStart } from './types';
import { modelRouter, taskRouter, trainRouter } from './routes';
import { ModelService, TrainService } from './services';
import { TaskService } from './services/task_service';

export class MlCommonsPlugin implements Plugin<MlCommonsPluginSetup, MlCommonsPluginStart> {
  private readonly logger: Logger;

  constructor(initializerContext: PluginInitializerContext) {
    this.logger = initializerContext.logger.get();
  }

  public setup(core: CoreSetup) {
    this.logger.debug('mlCommons: Setup');
    const router = core.http.createRouter();

    const trainOSClient = createTrainCluster(core);
    const modelOSClient = createModelCluster(core);
    const taskOSClient = createTaskCluster(core);

    const trainService = new TrainService(trainOSClient);
    const modelService = new ModelService(modelOSClient);
    const taskService = new TaskService(taskOSClient);

    const services = {
      trainService,
      modelService,
      taskService,
    };

    modelRouter(services, router);
    taskRouter(services, router);
    trainRouter(services, router);

    return {};
  }

  public start(core: CoreStart) {
    this.logger.debug('mlCommons: Started');
    return {};
  }

  public stop() { }
}
