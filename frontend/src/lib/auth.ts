import { writable } from 'svelte/store';
import { browser } from '$app/environment';

export interface AuthToken {
	accessToken: string;
	refreshToken: string;
	userId: string;
	username: string;
	role: string;
}

function createAuthStore() {
	const { subscribe, set, update } = writable<AuthToken | null>(null);

	// Load from localStorage on initialization
	if (browser) {
		const stored = localStorage.getItem('authToken');
		if (stored) {
			try {
				set(JSON.parse(stored));
			} catch (e) {
				localStorage.removeItem('authToken');
			}
		}
	}

	return {
		subscribe,
		setAuth: (token: AuthToken) => {
			set(token);
			if (browser) {
				localStorage.setItem('authToken', JSON.stringify(token));
			}
		},
		clearAuth: () => {
			set(null);
			if (browser) {
				localStorage.removeItem('authToken');
			}
		},
		updateToken: (accessToken: string) => {
			update((current) => {
				if (!current) return null;
				const updated = { ...current, accessToken };
				if (browser) {
					localStorage.setItem('authToken', JSON.stringify(updated));
				}
				return updated;
			});
		}
	};
}

export const authStore = createAuthStore();

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

