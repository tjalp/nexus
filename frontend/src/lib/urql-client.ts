import { Client, cacheExchange, fetchExchange } from '@urql/svelte';
import { authStore } from './auth';
import { get } from 'svelte/store';

// Create URQL client with authentication
export function createAuthenticatedClient() {
	return new Client({
		url: 'http://localhost:8080/graphql',
		exchanges: [cacheExchange, fetchExchange],
		preferGetMethod: false,
		fetchOptions: () => {
			const auth = get(authStore);
			return {
				headers: {
					authorization: auth ? `Bearer ${auth.accessToken}` : ''
				}
			};
		}
	});
}

export const urqlClient = createAuthenticatedClient();

