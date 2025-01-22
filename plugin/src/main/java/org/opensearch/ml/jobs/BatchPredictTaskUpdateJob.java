package org.opensearch.ml.jobs;

import java.io.IOException;
import java.time.Instant;

import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.core.xcontent.XContentParserUtils;
import org.opensearch.jobscheduler.spi.JobSchedulerExtension;
import org.opensearch.jobscheduler.spi.ScheduledJobParser;
import org.opensearch.jobscheduler.spi.ScheduledJobRunner;
import org.opensearch.jobscheduler.spi.schedule.ScheduleParser;
import org.opensearch.ml.common.CommonValue;

public class BatchPredictTaskUpdateJob implements JobSchedulerExtension {

    @Override
    public String getJobType() {
        return "checkBatchJobTaskStatus";
    }

    @Override
    public ScheduledJobRunner getJobRunner() {
        return MLBatchTaskUpdateJobRunner.getJobRunnerInstance();
    }

    @Override
    public ScheduledJobParser getJobParser() {
        return (parser, id, jobDocVersion) -> {
            MLBatchPredictTaskUpdateJobParameter jobParameter = new MLBatchPredictTaskUpdateJobParameter();
            XContentParserUtils.ensureExpectedToken(XContentParser.Token.START_OBJECT, parser.nextToken(), parser);

            while (!parser.nextToken().equals(XContentParser.Token.END_OBJECT)) {
                String fieldName = parser.currentName();
                parser.nextToken();
                switch (fieldName) {
                    case MLBatchPredictTaskUpdateJobParameter.NAME_FIELD:
                        jobParameter.setJobName(parser.text());
                        break;
                    case MLBatchPredictTaskUpdateJobParameter.ENABLED_FILED:
                        jobParameter.setEnabled(parser.booleanValue());
                        break;
                    case MLBatchPredictTaskUpdateJobParameter.ENABLED_TIME_FILED:
                        jobParameter.setEnabledTime(parseInstantValue(parser));
                        break;
                    case MLBatchPredictTaskUpdateJobParameter.LAST_UPDATE_TIME_FIELD:
                        jobParameter.setLastUpdateTime(parseInstantValue(parser));
                        break;
                    case MLBatchPredictTaskUpdateJobParameter.SCHEDULE_FIELD:
                        jobParameter.setSchedule(ScheduleParser.parse(parser));
                        break;
                    case MLBatchPredictTaskUpdateJobParameter.LOCK_DURATION_SECONDS:
                        jobParameter.setLockDurationSeconds(parser.longValue());
                        break;
                    case MLBatchPredictTaskUpdateJobParameter.JITTER:
                        jobParameter.setJitter(parser.doubleValue());
                        break;
                    default:
                        XContentParserUtils.throwUnknownToken(parser.currentToken(), parser.getTokenLocation());
                }
            }
            return jobParameter;
        };
    }

    private Instant parseInstantValue(XContentParser parser) throws IOException {
        if (XContentParser.Token.VALUE_NULL.equals(parser.currentToken())) {
            return null;
        }
        if (parser.currentToken().isValue()) {
            return Instant.ofEpochMilli(parser.longValue());
        }
        XContentParserUtils.throwUnknownToken(parser.currentToken(), parser.getTokenLocation());
        return null;
    }

    @Override
    public String getJobIndex() {
        return CommonValue.TASK_POLLING_JOB_INDEX;
    }

}
