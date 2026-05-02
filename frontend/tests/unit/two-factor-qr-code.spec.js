/* eslint-env jest */

const fs = require('fs')
const path = require('path')
const babel = require('@babel/core')

jest.mock('qrcode', () => ({
  toDataURL: jest.fn(() => Promise.resolve('data:image/png;base64,qr-code'))
}))

const QRCode = require('qrcode')

function loadTwoFactorQrCodeModule() {
  const filePath = path.resolve(__dirname, '../../src/utils/twoFactorQrCode.js')
  const source = fs.readFileSync(filePath, 'utf8')
  const transformed = babel.transformSync(source, {
    filename: filePath,
    presets: [require.resolve('@babel/preset-env')]
  })
  const module = { exports: {} }
  // eslint-disable-next-line no-new-func
  const fn = new Function('module', 'exports', 'require', transformed.code)
  fn(module, module.exports, require)
  return module.exports
}

describe('resolveTwoFactorQrSrc', () => {
  test('should convert otpauth uri into browser-safe qr data url', async () => {
    const { resolveTwoFactorQrSrc } = loadTwoFactorQrCodeModule()

    const result = await resolveTwoFactorQrSrc('otpauth://totp/demo?secret=123456')

    expect(QRCode.toDataURL).toHaveBeenCalledWith('otpauth://totp/demo?secret=123456', expect.objectContaining({
      margin: 1,
      width: 300
    }))
    expect(result).toBe('data:image/png;base64,qr-code')
  })

  test('should keep browser-safe src unchanged', async () => {
    const { resolveTwoFactorQrSrc } = loadTwoFactorQrCodeModule()

    await expect(resolveTwoFactorQrSrc('data:image/png;base64,abc')).resolves.toBe('data:image/png;base64,abc')
    await expect(resolveTwoFactorQrSrc('https://example.com/qr.png')).resolves.toBe('https://example.com/qr.png')
  })
})
