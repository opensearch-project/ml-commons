export const generateTermQuery = (key: string, value: string | number | Array<string | number>) => {
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

export const generateMustQueries = <T>(queries: T[]) => {
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
