/* eslint-env jest */

const fs = require('fs')
const path = require('path')

describe('router lazy import contract', () => {
  test('oj route modules do not keep webpack-specific chunk comments', () => {
    const routesSource = fs.readFileSync(
      path.resolve(__dirname, '../../src/pages/oj/router/routes.js'),
      'utf8'
    )
    const viewsSource = fs.readFileSync(
      path.resolve(__dirname, '../../src/pages/oj/views/index.js'),
      'utf8'
    )
    const classroomViewsSource = fs.readFileSync(
      path.resolve(__dirname, '../../src/pages/oj/views/classroom/index.js'),
      'utf8'
    )

    expect(routesSource).not.toMatch(/webpackChunkName/)
    expect(viewsSource).not.toMatch(/webpackChunkName/)
    expect(classroomViewsSource).not.toMatch(/webpackChunkName/)
  })
})
