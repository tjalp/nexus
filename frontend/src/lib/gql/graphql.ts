/* eslint-disable */
export type Maybe<T> = T | null;
export type InputMaybe<T> = T | null | undefined;
export type Exact<T extends { [key: string]: unknown }> = { [K in keyof T]: T[K] };
export type MakeOptional<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]?: Maybe<T[SubKey]> };
export type MakeMaybe<T, K extends keyof T> = Omit<T, K> & { [SubKey in K]: Maybe<T[SubKey]> };
export type MakeEmpty<T extends { [key: string]: unknown }, K extends keyof T> = { [_ in K]?: never };
export type Incremental<T> = T | { [P in keyof T]?: P extends ' $fragmentName' | '__typename' ? T[P] : never };
/** All built-in and custom scalars, mapped to their actual values */
export type Scalars = {
  ID: { input: string; output: string; }
  String: { input: string; output: string; }
  Boolean: { input: boolean; output: boolean; }
  Int: { input: number; output: number; }
  Float: { input: number; output: number; }
  Locale: { input: any; output: any; }
  /** The Long scalar type represents a signed 64-bit numeric non-fractional value */
  Long: { input: any; output: any; }
  /** The Short scalar type represents a signed 16-bit numeric non-fractional value */
  Short: { input: any; output: any; }
  TimeZone: { input: any; output: any; }
  UUID: { input: any; output: any; }
};

export type Duration = {
  __typename?: 'Duration';
  absoluteValue: Duration;
  inWholeDays: Scalars['Long']['output'];
  inWholeHours: Scalars['Long']['output'];
  inWholeMicroseconds: Scalars['Long']['output'];
  inWholeMilliseconds: Scalars['Long']['output'];
  inWholeMinutes: Scalars['Long']['output'];
  inWholeNanoseconds: Scalars['Long']['output'];
  inWholeSeconds: Scalars['Long']['output'];
};

export type DurationInput = {
  rawValue: Scalars['Long']['input'];
};

export type GeneralAttachment = {
  __typename?: 'GeneralAttachment';
  lastKnownName?: Maybe<Scalars['String']['output']>;
  preferredLocale: Scalars['Locale']['output'];
  timeZone?: Maybe<Scalars['TimeZone']['output']>;
};

export type Instant = {
  __typename?: 'Instant';
  epochSeconds: Scalars['Long']['output'];
  nanosecondsOfSecond: Scalars['Int']['output'];
};

/** Mutation object */
export type Mutation = {
  __typename?: 'Mutation';
  addPunishment?: Maybe<Array<Punishment>>;
  removePunishment?: Maybe<Array<Punishment>>;
  updateGeneralAttachment: ProfileSnapshot;
  updateNoticesAttachment: ProfileSnapshot;
};


/** Mutation object */
export type MutationAddPunishmentArgs = {
  duration?: InputMaybe<DurationInput>;
  id: Scalars['UUID']['input'];
  issuedBy?: InputMaybe<Scalars['String']['input']>;
  reason: Scalars['String']['input'];
  severity: PunishmentSeverity;
  type: PunishmentType;
};


/** Mutation object */
export type MutationRemovePunishmentArgs = {
  caseId: Scalars['String']['input'];
  id: Scalars['UUID']['input'];
};


/** Mutation object */
export type MutationUpdateGeneralAttachmentArgs = {
  id: Scalars['UUID']['input'];
  lastKnownName?: InputMaybe<Scalars['String']['input']>;
  preferredLocale?: InputMaybe<Scalars['Locale']['input']>;
  timeZone?: InputMaybe<Scalars['TimeZone']['input']>;
};


/** Mutation object */
export type MutationUpdateNoticesAttachmentArgs = {
  acceptedRulesVersion?: InputMaybe<Scalars['Int']['input']>;
  id: Scalars['UUID']['input'];
  seenRecommendations?: InputMaybe<Scalars['Boolean']['input']>;
};

export type NoticesAttachment = {
  __typename?: 'NoticesAttachment';
  acceptedRulesVersion: Scalars['Int']['output'];
  hasSeenRecommendations: Scalars['Boolean']['output'];
};

export type ProfileAttachment = GeneralAttachment | NoticesAttachment | PunishmentAttachment;

export type ProfileSnapshot = {
  __typename?: 'ProfileSnapshot';
  attachments: Array<ProfileAttachment>;
  createdAt: Instant;
  id: Scalars['UUID']['output'];
  modifiedAt: Instant;
};

export type Punishment = {
  __typename?: 'Punishment';
  caseId: Scalars['String']['output'];
  duration: Duration;
  expiresAt: Instant;
  isActive: Scalars['Boolean']['output'];
  issuedBy: Scalars['String']['output'];
  reason: Scalars['String']['output'];
  severity: PunishmentSeverity;
  timestamp: Instant;
  type: PunishmentType;
};

export type PunishmentAttachment = {
  __typename?: 'PunishmentAttachment';
  punishments: Array<Punishment>;
};

export enum PunishmentSeverity {
  Critical = 'CRITICAL',
  Major = 'MAJOR',
  Minor = 'MINOR',
  Moderate = 'MODERATE',
  Severe = 'SEVERE',
  Warning = 'WARNING'
}

export enum PunishmentType {
  Ban = 'BAN',
  Kick = 'KICK',
  Mute = 'MUTE',
  Warning = 'WARNING'
}

/** Query object */
export type Query = {
  __typename?: 'Query';
  /** Fetches a profile by its ID. */
  profile?: Maybe<ProfileSnapshot>;
};


/** Query object */
export type QueryProfileArgs = {
  id: Scalars['UUID']['input'];
};
