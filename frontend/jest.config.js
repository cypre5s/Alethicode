module.exports = {
  moduleFileExtensions: ['js', 'json', 'vue'],
  transform: {
    '^.+\\.js$': 'babel-jest'
  },
  moduleNameMapper: {
    '^@/(.*)$': '<rootDir>/src/$1',
    '^@oj/(.*)$': '<rootDir>/src/pages/oj/$1',
    '^@admin/(.*)$': '<rootDir>/src/pages/admin/$1'
  },
  testMatch: [
    '<rootDir>/tests/unit/**/*.spec.js'
  ],
  testEnvironment: 'jsdom'
}
