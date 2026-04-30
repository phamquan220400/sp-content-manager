import { faker } from '@faker-js/faker';

export type CreatorProfilePayload = {
  displayName: string;
  bio: string;
  creatorCategory: CreatorCategory;
  contentPreferences: string[];
};

/** Must match com.samuel.app.creator.model.CreatorProfile.CreatorCategory */
export type CreatorCategory =
  | 'LIFESTYLE'
  | 'GAMING'
  | 'EDUCATION'
  | 'TECH'
  | 'FINANCE'
  | 'FITNESS'
  | 'ENTERTAINMENT'
  | 'OTHER';

export function createProfilePayload(
  overrides: Partial<CreatorProfilePayload> = {},
): CreatorProfilePayload {
  return {
    displayName: faker.person.fullName().slice(0, 50),
    bio: faker.lorem.sentence(),
    creatorCategory: 'TECH',
    contentPreferences: ['tutorials', 'reviews'],
    ...overrides,
  };
}
