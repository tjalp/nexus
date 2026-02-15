
import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  overwrite: true,
  schema: "http://localhost:8080/graphql",
  generates: {
    "./src/lib/gql/": {
      preset: "client",
      plugins: [],
      config: {
        useTypeImports: true,
      }
    }
  }
};

export default config;
