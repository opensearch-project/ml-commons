import { AttributeValue, MapAttributeValue } from 'aws-sdk/clients/dynamodb';
import { DISCOVERY_SUMMARY_TOOL_PROMPT } from '../prompts/discover_summary_tool_prompt';
import { insertAgentAndAlias, OPENSEARCH_OLLY_AGENT_TYPE } from '../global_resource_helper';
import { DynamoDB } from 'aws-sdk';

export const DISCOVERY_SUMMARY_AGENT_ALIAS = 'os_indexInsightFlow'; // discovery summary agent alias.
export const DISCOVERY_SUMMARY_AGENT_ID = 'olly3-index-insight-flow-agent-id'; // discovery summary agent id.

export async function createAgent(modelId: string, db: DynamoDB): Promise<void> {
    //discovery summary agent.
    const indexInsightFlowAgentRequestBody: Record<string, any> = {
        name: { S: 'index insight flow agent' },
        description: { S: 'This is the index insight flow agent' },
        type: { S: 'flow' },
        created_time: { N: Date.now().toString() },
        is_hidden: { BOOL: true },
        tools: {
            L: [
                {
                    M: {
                        type: { S: 'IndexInsightTool' },
                        name: { S: 'Index insight Tool' },
                    } as Record<string, AttributeValue>,
        } as MapAttributeValue,
],
},
};
    await insertAgentAndAlias(
        DISCOVERY_SUMMARY_AGENT_ID,
        OPENSEARCH_OLLY_AGENT_TYPE,
        DISCOVERY_SUMMARY_AGENT_ALIAS,
        indexInsightFlowAgentRequestBody,
        db,
    );
}