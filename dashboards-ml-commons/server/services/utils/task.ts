import { generateTermQuery, generateMustQueries } from './query';

export const convertTaskSource = ({
  last_update_time,
  create_time,
  is_async,
  function_name,
  input_type,
  worker_node,
  state,
  model_id,
  task_type,
}: {
  last_update_time: number;
  create_time: number;
  is_async: boolean;
  function_name: string;
  input_type: string;
  worker_node: string;
  state: string;
  model_id: string;
  task_type: string;
}) => ({
  lastUpdateTime: last_update_time,
  createTime: create_time,
  isAsync: is_async,
  functionName: function_name,
  inputType: input_type,
  workerNode: worker_node,
  state,
  modelId: model_id,
  taskType: task_type,
});

export const generateTaskSearchQuery = ({
  ids,
  modelId,
  functionName,
  createdStart,
  createdEnd,
}: {
  ids?: string[];
  modelId?: string;
  functionName?: string;
  createdStart?: number;
  createdEnd?: number;
}) =>
  generateMustQueries([
    ...(ids ? [{ ids: { values: ids } }] : []),
    ...(functionName ? [generateTermQuery('function_name', functionName)] : []),
    ...(modelId
      ? [
          {
            wildcard: {
              model_id: {
                value: `*${modelId}*`,
              },
            },
          },
        ]
      : []),
    ...(createdStart || createdEnd
      ? [
          {
            range: {
              create_time: {
                ...(createdStart ? { gte: createdStart } : {}),
                ...(createdEnd ? { lte: createdEnd } : {}),
              },
            },
          },
        ]
      : []),
  ]);
