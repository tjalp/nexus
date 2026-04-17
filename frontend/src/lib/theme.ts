import { browser } from '$app/environment';

export type ThemeId = 'ocean' | 'midnight' | 'matrix' | 'sand' | 'aurora';

export type ThemeDefinition = {
	id: ThemeId;
	label: string;
	isDark: boolean;
	preview: string;
	values: {
		background: string;
		foreground: string;
		muted: string;
		mutedForeground: string;
		primary: string;
		primaryForeground: string;
		secondary: string;
		secondaryForeground: string;
		accent: string;
		accentForeground: string;
		panel: string;
		panelForeground: string;
	};
};

export const themes: ThemeDefinition[] = [
	{
		id: 'ocean',
		label: 'Oceanic',
		isDark: false,
		preview: 'linear-gradient(135deg, #2563eb, #0ea5e9)',
		values: {
			background: '#f6f8fd',
			foreground: '#0f172a',
			muted: '#e5e9f4',
			mutedForeground: '#475569',
			primary: '#2563eb',
			primaryForeground: '#f8fafc',
			secondary: '#0ea5e9',
			secondaryForeground: '#05202d',
			accent: '#7c3aed',
			accentForeground: '#fdfcff',
			panel: '#ffffff',
			panelForeground: '#0f172a'
		}
	},
	{
		id: 'midnight',
		label: 'Midnight',
		isDark: true,
		preview: 'linear-gradient(135deg, #111827, #312e81)',
		values: {
			background: '#0b1221',
			foreground: '#e5e7eb',
			muted: '#111827',
			mutedForeground: '#9ca3af',
			primary: '#60a5fa',
			primaryForeground: '#0b1221',
			secondary: '#8b5cf6',
			secondaryForeground: '#0b1221',
			accent: '#22d3ee',
			accentForeground: '#0b1221',
			panel: '#0f172a',
			panelForeground: '#e5e7eb'
		}
	},
	{
		id: 'matrix',
		label: 'Matrix',
		isDark: true,
		preview: 'linear-gradient(135deg, #0f172a, #16a34a)',
		values: {
			background: '#050910',
			foreground: '#d1fae5',
			muted: '#0b141f',
			mutedForeground: '#6ee7b7',
			primary: '#16a34a',
			primaryForeground: '#02120a',
			secondary: '#0ea5e9',
			secondaryForeground: '#031018',
			accent: '#22c55e',
			accentForeground: '#02120a',
			panel: '#0b1221',
			panelForeground: '#d1fae5'
		}
	},
	{
		id: 'sand',
		label: 'Sandstone',
		isDark: false,
		preview: 'linear-gradient(135deg, #f59e0b, #fcd34d)',
		values: {
			background: '#fffaf3',
			foreground: '#3f2d1c',
			muted: '#f7e6c9',
			mutedForeground: '#8b5e34',
			primary: '#f59e0b',
			primaryForeground: '#3f2d1c',
			secondary: '#fb923c',
			secondaryForeground: '#422006',
			accent: '#c084fc',
			accentForeground: '#2b0b3f',
			panel: '#ffffff',
			panelForeground: '#3f2d1c'
		}
	},
	{
		id: 'aurora',
		label: 'Aurora',
		isDark: true,
		preview: 'linear-gradient(135deg, #0ea5e9, #8b5cf6)',
		values: {
			background: '#0b1021',
			foreground: '#e2e8f0',
			muted: '#10172d',
			mutedForeground: '#94a3b8',
			primary: '#0ea5e9',
			primaryForeground: '#021018',
			secondary: '#8b5cf6',
			secondaryForeground: '#0b1021',
			accent: '#22d3ee',
			accentForeground: '#03222c',
			panel: '#0f172a',
			panelForeground: '#e2e8f0'
		}
	}
];

export function applyTheme(themeId: ThemeId) {
	if (!browser) return;
	const theme = themes.find((entry) => entry.id === themeId) ?? themes[0];
	const root = document.documentElement;

	root.style.setProperty('--background', theme.values.background);
	root.style.setProperty('--foreground', theme.values.foreground);
	root.style.setProperty('--muted', theme.values.muted);
	root.style.setProperty('--muted-foreground', theme.values.mutedForeground);
	root.style.setProperty('--primary', theme.values.primary);
	root.style.setProperty('--primary-foreground', theme.values.primaryForeground);
	root.style.setProperty('--secondary', theme.values.secondary);
	root.style.setProperty('--secondary-foreground', theme.values.secondaryForeground);
	root.style.setProperty('--accent', theme.values.accent);
	root.style.setProperty('--accent-foreground', theme.values.accentForeground);
	root.style.setProperty('--panel', theme.values.panel);
	root.style.setProperty('--panel-foreground', theme.values.panelForeground);

	root.classList.toggle('dark', theme.isDark);
}

export type FontChoice = 'plex' | 'jetbrains';
export type Roundness = 'minimal' | 'medium' | 'pill';

export function applyFont(font: FontChoice) {
	if (!browser) return;
	const root = document.documentElement;
	if (font === 'jetbrains') {
		root.style.setProperty('--font-sans', '"IBM Plex Sans", "Inter", "Segoe UI", system-ui, sans-serif');
		root.style.setProperty('--font-mono', '"JetBrains Mono", "Fira Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace');
	} else {
		root.style.setProperty('--font-sans', '"IBM Plex Sans", "Inter", "Segoe UI", system-ui, -apple-system, sans-serif');
		root.style.setProperty('--font-mono', '"Fira Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace');
	}
}

export function applyRoundness(radius: Roundness) {
	if (!browser) return;
	const root = document.documentElement;
	const value = radius === 'minimal' ? '6px' : radius === 'pill' ? '999px' : '14px';
	root.style.setProperty('--radius', value);
}
