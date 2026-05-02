/* eslint-env jest */

const { shouldReportRuntimeError } = require('../../vite.shared.js')

function runtimeErrorShouldDisplay(error) {
  return shouldReportRuntimeError(error)
}

function serializedRuntimeErrorShouldDisplay(error) {
  // eslint-disable-next-line no-eval
  return eval(`(${shouldReportRuntimeError.toString()})`)(error)
}

describe('vite runtime error filtering contract', () => {
  test('ignores known ResizeObserver browser noise only', () => {
    expect(runtimeErrorShouldDisplay(new Error('ResizeObserver loop limit exceeded'))).toBe(false)
    expect(runtimeErrorShouldDisplay(new Error('ResizeObserver loop completed with undelivered notifications.'))).toBe(false)

    expect(runtimeErrorShouldDisplay(new Error('TypeError: Cannot read properties of undefined'))).toBe(true)
    expect(runtimeErrorShouldDisplay(new Error('ReferenceError: foo is not defined'))).toBe(true)
  })

  test('serialized runtime error filter still works in browser context', () => {
    expect(serializedRuntimeErrorShouldDisplay(new Error('ResizeObserver loop limit exceeded'))).toBe(false)
    expect(serializedRuntimeErrorShouldDisplay(new Error('ReferenceError: foo is not defined'))).toBe(true)
  })
})
