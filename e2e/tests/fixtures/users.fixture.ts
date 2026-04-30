import { faker } from '@faker-js/faker';

export type RegistrationPayload = {
  email: string;
  password: string;
  confirmPassword: string;
};

export type LoginPayload = {
  email: string;
  password: string;
};

const DEFAULT_PASSWORD = 'P@ssword1234!';

/**
 * Creates a unique registration payload.
 * Uses a fixed password by default so tests can reliably log in afterward.
 */
export function createRegistrationPayload(
  overrides: Partial<RegistrationPayload> = {},
): RegistrationPayload {
  const password = overrides.password ?? DEFAULT_PASSWORD;
  return {
    email: faker.internet.email({ provider: 'test.example.com' }).toLowerCase(),
    password,
    confirmPassword: password,
    ...overrides,
  };
}

export function createLoginPayload(
  email: string,
  overrides: Partial<LoginPayload> = {},
): LoginPayload {
  return {
    email,
    password: DEFAULT_PASSWORD,
    ...overrides,
  };
}
