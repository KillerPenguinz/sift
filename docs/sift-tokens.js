// Sift design tokens + sample data — single source of truth for every surface.
// A "theme" is just a set of token values. Swapping the object re-skins the whole UI.
// This mirrors how it will be built natively (named tokens), so the work stays portable.

export const BUCKETS = {
  work:     { label: 'Work',     icon: 'work',   hue: 255 },
  personal: { label: 'Personal', icon: 'person', hue: 8 },
};

// Priority order = urgency. Accent hue runs a temperature: warm (asap) to cool (one day).
export const PRIORITY_META = [
  { key: 'asap',     name: 'asap',     icon: 'bolt' },
  { key: 'today',    name: 'today',    icon: 'wb_sunny' },
  { key: 'tomorrow', name: 'tomorrow', icon: 'wb_twilight' },
  { key: 'soon',     name: 'soon',     icon: 'eco' },
  { key: 'later',    name: 'later',    icon: 'event' },
  { key: 'oneday',   name: 'one day',  icon: 'bedtime' },
];

// Sample board content, shared across surfaces so screens feel like one product.
export const BOARD = {
  asap: { more: 0, tasks: [
    { t: 'Reply to the lease renewal email', b: 'personal' },
    { t: 'Send the Q3 deck to Priya', b: 'work', overdue: true },
  ]},
  today: { more: 1, tasks: [
    { t: 'Standup at 10:00', b: 'work', time: '10:00' },
    { t: 'Pick up the prescription', b: 'personal' },
    { t: 'Book the dentist', b: 'personal' },
  ]},
  tomorrow: { more: 0, tasks: [
    { t: 'Draft the onboarding copy', b: 'work' },
    { t: 'Water the plants', b: 'personal' },
  ]},
  soon: { more: 2, tasks: [
    { t: 'Plan the weekend trip', b: 'personal' },
    { t: 'Review the open pull requests', b: 'work' },
    { t: 'Renew the gym membership', b: 'personal' },
  ]},
  later: { more: 0, tasks: [
    { t: 'Read the design systems book', b: 'work' },
    { t: 'Refactor the export flow', b: 'work' },
  ]},
  oneday: { more: 3, tasks: [
    { t: 'Learn to make fresh pasta', b: 'personal' },
    { t: 'Sketch the app redesign', b: 'work' },
  ]},
};

// Fuller task list used by the focused single-priority view (surface C).
export const TODAY_FULL = [
  { t: 'Standup at 10:00', b: 'work', time: '10:00', note: 'Share the export blocker' },
  { t: 'Send the Q3 deck to Priya', b: 'work', overdue: true, note: 'Was due yesterday' },
  { t: 'Pick up the prescription', b: 'personal', time: '14:30' },
  { t: 'Book the dentist', b: 'personal' },
  { t: 'Call the landlord back', b: 'personal' },
  { t: 'Review the open pull requests', b: 'work', note: '3 waiting' },
  { t: 'Submit the expense report', b: 'work', done: true },
  { t: 'Reschedule the gym session', b: 'personal', done: true },
];

// ---- THEMES -------------------------------------------------------------
// Each theme lists every token explicitly. accent.* = per-priority urgency colors.
// bucket.* = per-bucket colors. headerMix / pillMix / bucketBgMix tune how much
// accent tints a surface. isDark flips accent-text computation (lighten vs darken).

export const THEMES = {
  paper: {
    label: 'Paper', sub: 'default · calm warm light', kind: 'light', isDark: false,
    bg: 'oklch(0.967 0.006 74)', surface: 'oklch(0.996 0.004 80)', surfaceRaised: 'oklch(1 0 0)',
    border: 'oklch(0.905 0.007 74)', borderStrong: 'oklch(0.85 0.009 74)',
    textPrimary: 'oklch(0.29 0.012 60)', textSecondary: 'oklch(0.52 0.012 60)', textTertiary: 'oklch(0.66 0.01 60)',
    headerMix: 8, pillMix: 16, bucketBgMix: 15, accentTextMix: 72,
    scrim: 'oklch(0.29 0.012 60 / 0.42)',
    accent: { asap:'oklch(0.605 0.155 27)', today:'oklch(0.7 0.14 65)', tomorrow:'oklch(0.72 0.12 95)', soon:'oklch(0.64 0.11 152)', later:'oklch(0.6 0.09 232)', oneday:'oklch(0.57 0.1 292)' },
    bucket: { work:'oklch(0.55 0.13 255)', personal:'oklch(0.62 0.15 8)' },
  },

  slate: {
    label: 'Slate', sub: 'calm dark · cool neutral', kind: 'dark', isDark: true,
    bg: 'oklch(0.215 0.012 256)', surface: 'oklch(0.262 0.014 256)', surfaceRaised: 'oklch(0.305 0.016 256)',
    border: 'oklch(0.36 0.016 256)', borderStrong: 'oklch(0.45 0.02 256)',
    textPrimary: 'oklch(0.96 0.004 256)', textSecondary: 'oklch(0.75 0.012 256)', textTertiary: 'oklch(0.6 0.014 256)',
    headerMix: 18, pillMix: 28, bucketBgMix: 26, accentTextMix: 30,
    scrim: 'oklch(0.12 0.01 256 / 0.6)',
    accent: { asap:'oklch(0.7 0.16 25)', today:'oklch(0.8 0.14 70)', tomorrow:'oklch(0.84 0.13 95)', soon:'oklch(0.76 0.13 158)', later:'oklch(0.72 0.11 238)', oneday:'oklch(0.72 0.13 298)' },
    bucket: { work:'oklch(0.7 0.13 252)', personal:'oklch(0.72 0.16 12)' },
  },

  ink: {
    label: 'Ink', sub: 'bold high contrast · punchy', kind: 'bold', isDark: true,
    bg: 'oklch(0.16 0.014 285)', surface: 'oklch(0.205 0.018 285)', surfaceRaised: 'oklch(0.25 0.02 285)',
    border: 'oklch(0.33 0.024 285)', borderStrong: 'oklch(0.46 0.03 285)',
    textPrimary: 'oklch(0.98 0.004 285)', textSecondary: 'oklch(0.78 0.014 285)', textTertiary: 'oklch(0.62 0.018 285)',
    headerMix: 30, pillMix: 100, bucketBgMix: 34, accentTextMix: 22, boldFill: true,
    scrim: 'oklch(0.08 0.012 285 / 0.66)',
    accent: { asap:'oklch(0.68 0.23 25)', today:'oklch(0.8 0.19 65)', tomorrow:'oklch(0.86 0.18 100)', soon:'oklch(0.78 0.2 150)', later:'oklch(0.68 0.2 250)', oneday:'oklch(0.66 0.24 305)' },
    bucket: { work:'oklch(0.68 0.19 255)', personal:'oklch(0.7 0.22 12)' },
  },

  linen: {
    label: 'Linen', sub: 'warm light · cozy', kind: 'warm', isDark: false,
    bg: 'oklch(0.945 0.02 70)', surface: 'oklch(0.985 0.013 72)', surfaceRaised: 'oklch(1 0.006 72)',
    border: 'oklch(0.88 0.018 66)', borderStrong: 'oklch(0.81 0.024 62)',
    textPrimary: 'oklch(0.32 0.026 48)', textSecondary: 'oklch(0.5 0.028 50)', textTertiary: 'oklch(0.64 0.026 54)',
    headerMix: 11, pillMix: 20, bucketBgMix: 18, accentTextMix: 66,
    scrim: 'oklch(0.32 0.026 48 / 0.4)',
    accent: { asap:'oklch(0.58 0.16 30)', today:'oklch(0.66 0.14 58)', tomorrow:'oklch(0.68 0.12 85)', soon:'oklch(0.6 0.11 140)', later:'oklch(0.58 0.1 222)', oneday:'oklch(0.56 0.12 318)' },
    bucket: { work:'oklch(0.53 0.13 250)', personal:'oklch(0.58 0.16 18)' },
  },

  cyber: {
    label: 'Cyber', sub: 'cool dark · electric', kind: 'cool', isDark: true,
    bg: 'oklch(0.19 0.03 220)', surface: 'oklch(0.235 0.035 220)', surfaceRaised: 'oklch(0.28 0.04 218)',
    border: 'oklch(0.36 0.045 216)', borderStrong: 'oklch(0.5 0.07 200)',
    textPrimary: 'oklch(0.95 0.02 200)', textSecondary: 'oklch(0.74 0.04 205)', textTertiary: 'oklch(0.6 0.05 210)',
    headerMix: 22, pillMix: 34, bucketBgMix: 30, accentTextMix: 26,
    scrim: 'oklch(0.1 0.03 220 / 0.64)',
    accent: { asap:'oklch(0.72 0.2 8)', today:'oklch(0.82 0.16 75)', tomorrow:'oklch(0.88 0.18 175)', soon:'oklch(0.82 0.2 165)', later:'oklch(0.75 0.16 232)', oneday:'oklch(0.74 0.18 290)' },
    bucket: { work:'oklch(0.78 0.15 230)', personal:'oklch(0.74 0.19 10)' },
  },
};

export const THEME_ORDER = ['paper', 'slate', 'ink', 'linen', 'cyber'];

// Derive every computed value a surface needs from a theme + the board data.
export function derive(theme) {
  const mix = (c, p, base) => `color-mix(in oklab, ${c} ${p}, ${base})`;
  const accentText = (c) => theme.isDark ? mix(c, `${100 - theme.accentTextMix}%`, 'white') : mix(c, `${theme.accentTextMix}%`, 'black');

  const buckets = {};
  for (const k in BUCKETS) {
    const b = BUCKETS[k], color = theme.bucket[k];
    buckets[k] = { ...b, color, bg: mix(color, `${theme.bucketBgMix}%`, theme.surface),
      onColor: theme.isDark ? mix(color, '12%', 'white') : color };
  }

  const priorities = PRIORITY_META.map(meta => {
    const accent = theme.accent[meta.key];
    const data = BOARD[meta.key];
    const aText = accentText(accent);
    return {
      ...meta, accent, accentText: aText,
      headerBg: theme.boldFill ? mix(accent, `${theme.headerMix}%`, theme.surface) : mix(accent, `${theme.headerMix}%`, theme.surface),
      pillBg: theme.boldFill ? accent : mix(accent, `${theme.pillMix}%`, theme.surface),
      pillText: theme.boldFill ? mix(accent, '92%', theme.isDark ? 'black' : 'white') : aText,
      count: data.tasks.length + (data.more || 0),
      hasMore: (data.more || 0) > 0,
      moreLabel: `+${data.more} more`,
      tasks: data.tasks.map(t => {
        const b = buckets[t.b];
        const hasTime = !!t.time, isOverdue = !!t.overdue;
        return { t: t.t, bucketLabel: b.label, bucketIcon: b.icon, bucketColor: b.onColor, bucketBg: b.bg,
          hasTime, timeText: t.time || '', isOverdue, showTime: hasTime && !isOverdue,
          subMeta: b.label + (hasTime ? '  \u00b7  ' + t.time : '') };
      }),
    };
  });

  const total = PRIORITY_META.reduce((n, m) => n + BOARD[m.key].tasks.length + (BOARD[m.key].more || 0), 0);
  const asap = theme.accent.asap;

  return {
    theme, mix, buckets, priorities, total,
    overdueBg: mix(asap, theme.isDark ? '24%' : '13%', theme.surface),
    overdueText: theme.isDark ? mix(asap, '20%', 'white') : mix(asap, '74%', 'black'),
  };
}
