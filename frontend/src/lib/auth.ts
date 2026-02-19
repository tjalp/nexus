import { writable, derived } from 'svelte/store';
import { browser } from '$app/environment';
import {goto} from "$app/navigation";

export interface AuthToken {
	accessToken: string;
	refreshToken: string;
	userId: string;
	username: string;
	role: string;
}

const STORAGE_KEY = 'authToken';
let refreshTimeout: ReturnType<typeof setTimeout> | null = null;

function createAuthStore() {
	const { subscribe, set, update } = writable<AuthToken | null>(null);

	// Load from localStorage on initialization
	if (browser) {
		const stored = localStorage.getItem(STORAGE_KEY);
		if (stored) {
			try {
				const token = JSON.parse(stored);
				set(token);
				scheduleTokenRefresh(token);
			} catch (e) {
				localStorage.removeItem(STORAGE_KEY);
			}
		}
	}

	function scheduleTokenRefresh(token: AuthToken) {
		if (!browser) return;

		// Clear existing timeout
		if (refreshTimeout) {
			clearTimeout(refreshTimeout);
		}

		// Decode JWT to get expiration time
		try {
			const payload = JSON.parse(atob(token.accessToken.split('.')[1]));
			const expiresAt = payload.exp * 1000; // Convert to milliseconds
			const now = Date.now();
			const timeUntilExpiry = expiresAt - now;

			// Refresh 1 minute before expiry
			const refreshTime = Math.max(0, timeUntilExpiry - 60000);

			refreshTimeout = setTimeout(async () => {
				try {
					await refreshAuthToken();
				} catch (error) {
					console.error('Failed to refresh token:', error);
					clearAuth();
				}
			}, refreshTime);
		} catch (e) {
			console.error('Failed to parse token:', e);
		}
	}

	async function refreshAuthToken() {
		const currentToken = await new Promise<AuthToken | null>((resolve) => {
			const unsubscribe = subscribe((value) => {
				unsubscribe();
				resolve(value);
			});
		});

		if (!currentToken) return;

		const response = await fetch('http://localhost:8080/auth/refresh', {
			method: 'POST',
			headers: {
				'Content-Type': 'application/json'
			},
			body: JSON.stringify({ refreshToken: currentToken.refreshToken })
		});

		if (!response.ok) {
			throw new Error('Failed to refresh token');
		}

		const newToken: AuthToken = await response.json();
		setAuth(newToken);
	}

	function setAuth(token: AuthToken) {
		set(token);
		if (browser) {
			localStorage.setItem(STORAGE_KEY, JSON.stringify(token));
			scheduleTokenRefresh(token);
		}
	}

	function clearAuth() {
		set(null);
		if (browser) {
			localStorage.removeItem(STORAGE_KEY);
			if (refreshTimeout) {
				clearTimeout(refreshTimeout);
				refreshTimeout = null;
			}
			// Redirect to login page
			goto('/login');
			// window.location.href = '/login';
		}
	}

	function updateAccessToken(accessToken: string) {
		update((current) => {
			if (!current) return null;
			const updated = { ...current, accessToken };
			if (browser) {
				localStorage.setItem(STORAGE_KEY, JSON.stringify(updated));
				scheduleTokenRefresh(updated);
			}
			return updated;
		});
	}

	return {
		subscribe,
		setAuth,
		clearAuth,
		updateToken: updateAccessToken,
		refresh: refreshAuthToken
	};
}

export const authStore = createAuthStore();

// Derived store for checking if user is authenticated
export const isAuthenticated = derived(authStore, ($authStore) => $authStore !== null);

// Derived store for user role
export const userRole = derived(authStore, ($authStore) => $authStore?.role ?? null);

// Login function
export async function login(username: string, password: string): Promise<AuthToken> {
	const response = await fetch('http://localhost:8080/auth/login', {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json'
		},
		body: JSON.stringify({ username, password })
	});

	if (!response.ok) {
		const error = await response.json();
		throw new Error(error.error || 'Login failed');
	}

	const token = await response.json();
	authStore.setAuth(token);
	return token;
}

// Register function
export async function register(
	username: string,
	password: string,
	profileId: string
): Promise<AuthToken> {
	const response = await fetch('http://localhost:8080/auth/register', {
		method: 'POST',
		headers: {
			'Content-Type': 'application/json'
		},
		body: JSON.stringify({ username, password, profileId })
	});

	if (!response.ok) {
		const error = await response.json();
		throw new Error(error.error || 'Registration failed');
	}

	const token = await response.json();
	authStore.setAuth(token);
	return token;
}

// Logout function
export function logout() {
	authStore.clearAuth();
}



