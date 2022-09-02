export const convertModelSource = ({
  model_content,
  name,
  algorithm,
  model_context,
}: {
  model_content: string;
  name: string;
  algorithm: string;
  model_context: string;
}) => ({
  content: model_content,
  name,
  algorithm,
  context: model_context,
});

const generateTermQuery = (key: string, value: string | number | Array<string | number>) => {
  if (typeof value === 'string' || typeof value === 'number') {
    return {
      term: {
        [key]: { value: value },
      },
    };
  }
  return {
    terms: {
      [key]: value,
    },
  };
};

const genereateContextQuery = (context: Record<string, Array<string | number>>) => {
  const keys = Object.keys(context);
  return keys.map((key) => {
    const value = context[key];
    const fieldKey = `model_context.${key}`;

    if (typeof value[0] === 'string') {
      return {
        bool: {
          should: value.map((text) => ({
            match: {
              [fieldKey]: text,
            },
          })),
        },
      };
    }
    return generateTermQuery(fieldKey, value);
  });
};

export const generateModelSearchQuery = ({
  ids,
  algorithms,
  context,
}: {
  ids?: string[];
  algorithms?: string[];
  context?: Record<string, Array<string | number>>;
}) => {
  const queries = [
    ...(ids ? [{ ids: { value: ids } }] : []),
    ...(algorithms ? [generateTermQuery('algorithm', algorithms)] : []),
    ...(context ? genereateContextQuery(context) : []),
  ];

  switch (queries.length) {
    case 0:
      return {
        match_all: {},
      };
    case 1:
      return queries[0];
    default:
      return {
        bool: {
          must: queries,
        },
      };
  }
};
