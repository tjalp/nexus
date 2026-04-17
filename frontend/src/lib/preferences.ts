import { browser } from '$app/environment';
import { writable } from 'svelte/store';
import { applyFont, applyRoundness, applyTheme, type FontChoice, type Roundness, type ThemeId } from './theme';

type Preferences = {
	theme: ThemeId;
	font: FontChoice;
	radius: Roundness;
};

const STORAGE_KEY = 'nexus.ui-preferences';

const defaultPreferences: Preferences = {
	theme: 'ocean',
	font: 'jetbrains',
	radius: 'medium'
};

function loadPreferences(): Preferences {
	if (!browser) return defaultPreferences;
	const raw = localStorage.getItem(STORAGE_KEY);
	if (!raw) return defaultPreferences;
	try {
		const parsed = JSON.parse(raw) as Partial<Preferences>;
		return { ...defaultPreferences, ...parsed };
	} catch {
		return defaultPreferences;
	}
}

function persist(value: Preferences) {
	if (!browser) return;
	localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
}

export const preferenceStore = writable<Preferences>(defaultPreferences, (set) => {
	const initial = loadPreferences();
	set(initial);
	if (browser) {
		applyTheme(initial.theme);
		applyFont(initial.font);
		applyRoundness(initial.radius);
	}
	return () => {};
});

export function updatePreferences(partial: Partial<Preferences>) {
	preferenceStore.update((current) => {
		const next = { ...current, ...partial };
		applyTheme(next.theme);
		applyFont(next.font);
		applyRoundness(next.radius);
		persist(next);
		return next;
	});
}

export function resetPreferences() {
	preferenceStore.set(defaultPreferences);
	if (browser) {
		applyTheme(defaultPreferences.theme);
		applyFont(defaultPreferences.font);
		applyRoundness(defaultPreferences.radius);
		persist(defaultPreferences);
	}
}

export { defaultPreferences };
