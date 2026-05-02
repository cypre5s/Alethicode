/* eslint-env jest */

const fs = require('fs')
const path = require('path')

function readComponent(relativePath) {
  return fs.readFileSync(path.resolve(__dirname, '../../src', relativePath), 'utf-8')
}

describe('ErrorDiagnosisCard similar_error_refs contract', () => {
  const source = readComponent('pages/oj/views/problem/cards/ErrorDiagnosisCard.vue')

  it('should render similar refs with entry_date and excerpt', () => {
    expect(source).toContain('ed-similar-refs')
    expect(source).toContain('ed-similar-date')
    expect(source).toContain('ed-similar-excerpt')
    expect(source).toContain('formatRelativeDate')
  })

  it('should color code notebook vs memory hits', () => {
    expect(source).toContain('chip-similar_memory')
    expect(source).toContain("'chip-' + ref.source_type")
  })

  it('should have amber color scheme for notebook chips', () => {
    expect(source).toContain('#fef3c7')
    expect(source).toContain('#92400e')
  })

  it('should have blue color scheme for memory chips', () => {
    expect(source).toContain('#dbeafe')
    expect(source).toContain('#1e40af')
  })

  it('chips should have touch-friendly min-height', () => {
    expect(source).toContain('min-height: 44px')
  })
})

describe('error_diagnosis.schema.json similar_error_refs', () => {
  const schema = JSON.parse(fs.readFileSync(
    path.resolve(__dirname, '../../../contracts/tutor_workflow/cards/error_diagnosis.schema.json'), 'utf-8'))

  it('should have similar_error_refs as optional array property', () => {
    expect(schema.properties).toHaveProperty('similar_error_refs')
    expect(schema.properties.similar_error_refs.type).toBe('array')
    expect(schema.required).not.toContain('similar_error_refs')
  })

  it('each ref should require source_type and source_id', () => {
    const itemSchema = schema.properties.similar_error_refs.items
    expect(itemSchema.required).toContain('source_type')
    expect(itemSchema.required).toContain('source_id')
  })

  it('should support entry_date and excerpt fields', () => {
    const props = schema.properties.similar_error_refs.items.properties
    expect(props).toHaveProperty('entry_date')
    expect(props).toHaveProperty('excerpt')
  })
})
