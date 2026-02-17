
import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  overwrite: true,
  schema: "http://localhost:8080/graphql",
  documents: ["src/**/*.svelte", "src/**/*.ts", "src/**/*.svelte.ts"],
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
