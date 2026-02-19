import { Client, cacheExchange, fetchExchange, errorExchange } from '@urql/svelte';
import { authStore } from './auth';
import { get } from 'svelte/store';

// Create URQL client with authentication and error handling
export const urqlClient = new Client({
	url: 'http://localhost:8080/graphql',
	exchanges: [
		cacheExchange,
		errorExchange({
			onError: async (error) => {
				// Check if error is authentication related
				const isAuthError = error.graphQLErrors.some(
					(e) => e.message.includes('Authentication required') ||
					       e.message.includes('Token is not valid')
				);

				if (isAuthError) {
					console.error('Authentication error, attempting to refresh token...');
					try {
						await authStore.refresh();
					} catch (refreshError) {
						console.error('Failed to refresh token:', refreshError);
						authStore.clearAuth();
					}
				}
			}
		}),
		fetchExchange
	],
	preferGetMethod: false,
	fetchOptions: () => {
		const auth = get(authStore);
		const headers: Record<string, string> = {
			'Content-Type': 'application/json'
		};

		if (auth?.accessToken) {
			headers['Authorization'] = `Bearer ${auth.accessToken}`;
			console.log('DEBUG: Sending auth header with token:', auth.accessToken.substring(0, 20) + '...');
		} else {
			console.log('DEBUG: No auth token available');
		}

		return { headers };
	}
});


