#!/usr/bin/env python3
"""
Test the profile page with Firefox, bypassing some dependency checks
"""
import asyncio
import sys
import os
from playwright.async_api import async_playwright

async def main():
    try:
        async with async_playwright() as p:
            print("Launching Firefox (headless)...")
            
            # Try to launch Firefox with minimal dependencies
            browser = await p.firefox.launch(
                headless=True,
                firefox_user_prefs={
                    'media.navigator.streams.fake': True,
                    'media.navigator.permission.disabled': True,
                }
            )
            
            print("✓ Browser launched")
            
            page = await browser.new_page(viewport={'width': 1920, 'height': 1080})
            
            # Collect console messages
            console_logs = []
            def handle_console(msg):
                console_logs.append(f"[{msg.type}] {msg.text}")
            page.on('console', handle_console)
            
            # Collect errors
            errors = []
            def handle_error(err):
                errors.append(str(err))
            page.on('pageerror', handle_error)
            
            print("\nNavigating to http://localhost:8080/setting/profile...")
            response = await page.goto('http://localhost:8080/setting/profile', 
                                      wait_until='networkidle',
                                      timeout=30000)
            
            print(f"✓ Page loaded: Status {response.status}")
            print(f"✓ URL: {page.url}")
            
            # Wait for Vue to render
            await page.wait_for_timeout(3000)
            
            # Take screenshot
            screenshot_path = '/home/cypress/alethicode/screenshot_profile.png'
            await page.screenshot(path=screenshot_path, full_page=True)
            print(f"✓ Screenshot saved: {screenshot_path}")
            
            # Get page title
            title = await page.title()
            print(f"\n📄 Page Title: {title}")
            
            # Analyze page structure
            print("\n" + "="*80)
            print("PAGE STRUCTURE ANALYSIS")
            print("="*80)
            
            # Check for main elements using various selectors
            checks = [
                ('Sidebar', '.profile-sidebar, [class*="sidebar"], .st-sidebar'),
                ('Avatar section', '.avatar-section, [class*="avatar"], .st-card'),
                ('Upload zone', '.upload-zone, [class*="upload"]'),
                ('Form inputs', 'input, textarea, select'),
                ('Navigation items', '.nav-item, [class*="nav"], .st-nav-item'),
                ('Buttons', 'button'),
                ('Cards', '.card, .st-card, [class*="card"]'),
            ]
            
            for name, selector in checks:
                try:
                    elements = await page.query_selector_all(selector)
                    count = len(elements)
                    print(f"{'✓' if count > 0 else '✗'} {name}: {count} element(s)")
                except Exception as e:
                    print(f"✗ {name}: Error - {str(e)[:50]}")
            
            # Get visible text
            print("\n" + "="*80)
            print("VISIBLE TEXT CONTENT (first 1000 chars)")
            print("="*80)
            try:
                visible_text = await page.evaluate('() => document.body.innerText')
                print(visible_text[:1000])
                print("..." if len(visible_text) > 1000 else "")
            except Exception as e:
                print(f"Error getting text: {e}")
            
            # Get HTML structure
            print("\n" + "="*80)
            print("HTML STRUCTURE SAMPLE")
            print("="*80)
            try:
                body_html = await page.evaluate('() => document.body.innerHTML')
                # Show first 2000 chars of HTML
                print(body_html[:2000])
                print("..." if len(body_html) > 2000 else "")
            except Exception as e:
                print(f"Error getting HTML: {e}")
            
            # Test interactions
            print("\n" + "="*80)
            print("TESTING INTERACTIONS")
            print("="*80)
            
            # Try to find and click on elements
            try:
                buttons = await page.query_selector_all('button')
                print(f"\nFound {len(buttons)} buttons")
                
                if len(buttons) > 0:
                    for i, btn in enumerate(buttons[:3]):
                        try:
                            text = await btn.inner_text()
                            is_visible = await btn.is_visible()
                            print(f"  Button {i+1}: '{text.strip()[:30]}' (visible: {is_visible})")
                        except:
                            pass
            except Exception as e:
                print(f"Error testing buttons: {e}")
            
            # Try to find inputs
            try:
                inputs = await page.query_selector_all('input[type="text"], input[type="email"], textarea')
                print(f"\nFound {len(inputs)} text inputs")
                
                if len(inputs) > 0:
                    for i, inp in enumerate(inputs[:3]):
                        try:
                            placeholder = await inp.get_attribute('placeholder')
                            name = await inp.get_attribute('name')
                            print(f"  Input {i+1}: name='{name}', placeholder='{placeholder}'")
                        except:
                            pass
            except Exception as e:
                print(f"Error testing inputs: {e}")
            
            # Console logs
            print("\n" + "="*80)
            print("CONSOLE MESSAGES")
            print("="*80)
            if console_logs:
                for log in console_logs[:20]:  # Show first 20
                    print(log)
                if len(console_logs) > 20:
                    print(f"... and {len(console_logs) - 20} more")
            else:
                print("✓ No console messages")
            
            # Errors
            print("\n" + "="*80)
            print("JAVASCRIPT ERRORS")
            print("="*80)
            if errors:
                for err in errors:
                    print(f"❌ {err}")
            else:
                print("✓ No JavaScript errors detected")
            
            # Summary
            print("\n" + "="*80)
            print("TEST SUMMARY")
            print("="*80)
            print(f"✓ Page loaded successfully (Status: {response.status})")
            print(f"✓ Screenshot captured: {screenshot_path}")
            print(f"✓ Console logs: {len(console_logs)}")
            print(f"{'✓' if not errors else '✗'} JavaScript errors: {len(errors)}")
            print("="*80)
            
            await browser.close()
            print("\n✓ Test completed successfully")
            return 0
            
    except Exception as e:
        print(f"\n❌ ERROR: {type(e).__name__}: {str(e)}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        return 1

if __name__ == '__main__':
    exit_code = asyncio.run(main())
    sys.exit(exit_code)
