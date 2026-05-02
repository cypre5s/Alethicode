#!/usr/bin/env python3
"""
Test script to verify the profile settings page works correctly.
"""
import asyncio
from playwright.async_api import async_playwright
import json

async def test_profile_page():
    async with async_playwright() as p:
        # Launch browser
        browser = await p.chromium.launch(headless=True)
        context = await browser.new_context(viewport={'width': 1920, 'height': 1080})
        page = await context.new_page()
        
        # Collect console messages
        console_messages = []
        page.on('console', lambda msg: console_messages.append({
            'type': msg.type,
            'text': msg.text
        }))
        
        # Collect errors
        errors = []
        page.on('pageerror', lambda err: errors.append(str(err)))
        
        try:
            print("=" * 80)
            print("NAVIGATING TO: http://localhost:8080/setting/profile")
            print("=" * 80)
            
            # Navigate to the page
            response = await page.goto('http://localhost:8080/setting/profile', wait_until='networkidle', timeout=30000)
            
            print(f"\n✓ Page loaded successfully")
            print(f"  Status: {response.status}")
            print(f"  URL: {page.url}")
            
            # Wait a bit for any dynamic content
            await page.wait_for_timeout(2000)
            
            # Take initial screenshot
            await page.screenshot(path='/home/cypress/alethicode/screenshot_initial.png', full_page=True)
            print(f"\n✓ Screenshot saved: screenshot_initial.png")
            
            # Check page title
            title = await page.title()
            print(f"\n📄 Page Title: {title}")
            
            # Get page content structure
            print("\n" + "=" * 80)
            print("PAGE STRUCTURE ANALYSIS")
            print("=" * 80)
            
            # Check for main layout elements
            sidebar = await page.query_selector('.profile-sidebar, [class*="sidebar"]')
            print(f"\n{'✓' if sidebar else '✗'} Sidebar found: {bool(sidebar)}")
            
            avatar_section = await page.query_selector('.avatar-section, [class*="avatar"]')
            print(f"{'✓' if avatar_section else '✗'} Avatar section found: {bool(avatar_section)}")
            
            upload_zone = await page.query_selector('.upload-zone, [class*="upload"]')
            print(f"{'✓' if upload_zone else '✗'} Upload zone found: {bool(upload_zone)}")
            
            form_fields = await page.query_selector_all('input, textarea, select')
            print(f"{'✓' if form_fields else '✗'} Form fields found: {len(form_fields)} fields")
            
            # Check for navigation items
            nav_items = await page.query_selector_all('.nav-item, [class*="nav-item"], .sidebar-item, [class*="sidebar-item"]')
            print(f"{'✓' if nav_items else '✗'} Navigation items found: {len(nav_items)} items")
            
            # Get visible text content
            body_text = await page.evaluate('() => document.body.innerText')
            print(f"\n📝 Visible text preview (first 500 chars):")
            print("-" * 80)
            print(body_text[:500])
            print("-" * 80)
            
            # Try to find and click navigation items
            print("\n" + "=" * 80)
            print("TESTING NAVIGATION INTERACTIONS")
            print("=" * 80)
            
            if nav_items:
                print(f"\nFound {len(nav_items)} navigation items. Testing first 3...")
                for i, item in enumerate(nav_items[:3]):
                    try:
                        text = await item.inner_text()
                        print(f"\n  [{i+1}] Clicking: '{text.strip()}'")
                        await item.click()
                        await page.wait_for_timeout(500)
                        await page.screenshot(path=f'/home/cypress/alethicode/screenshot_nav_{i+1}.png')
                        print(f"      ✓ Clicked successfully, screenshot saved")
                    except Exception as e:
                        print(f"      ✗ Error clicking: {str(e)[:100]}")
            
            # Test form interactions
            print("\n" + "=" * 80)
            print("TESTING FORM INTERACTIONS")
            print("=" * 80)
            
            if form_fields:
                print(f"\nFound {len(form_fields)} form fields. Testing first 3...")
                for i, field in enumerate(form_fields[:3]):
                    try:
                        tag = await field.evaluate('el => el.tagName')
                        field_type = await field.evaluate('el => el.type || el.tagName')
                        placeholder = await field.evaluate('el => el.placeholder || ""')
                        name = await field.evaluate('el => el.name || el.id || ""')
                        
                        print(f"\n  [{i+1}] Field: {tag} (type: {field_type})")
                        print(f"      Name/ID: {name}")
                        print(f"      Placeholder: {placeholder}")
                        
                        if tag.lower() == 'input':
                            await field.click()
                            await field.fill('Test Value')
                            await page.wait_for_timeout(300)
                            print(f"      ✓ Filled with test value")
                    except Exception as e:
                        print(f"      ✗ Error interacting: {str(e)[:100]}")
            
            # Take final screenshot
            await page.screenshot(path='/home/cypress/alethicode/screenshot_final.png', full_page=True)
            print(f"\n✓ Final screenshot saved: screenshot_final.png")
            
            # Console messages
            print("\n" + "=" * 80)
            print("CONSOLE MESSAGES")
            print("=" * 80)
            
            if console_messages:
                for msg in console_messages:
                    icon = "⚠️" if msg['type'] == 'warning' else "❌" if msg['type'] == 'error' else "ℹ️"
                    print(f"{icon} [{msg['type'].upper()}] {msg['text']}")
            else:
                print("✓ No console messages")
            
            # Errors
            print("\n" + "=" * 80)
            print("JAVASCRIPT ERRORS")
            print("=" * 80)
            
            if errors:
                for err in errors:
                    print(f"❌ {err}")
            else:
                print("✓ No JavaScript errors detected")
            
            print("\n" + "=" * 80)
            print("TEST SUMMARY")
            print("=" * 80)
            print(f"✓ Page loaded successfully")
            print(f"✓ Screenshots captured: 3+ images")
            print(f"{'✓' if sidebar else '✗'} Sidebar rendering: {bool(sidebar)}")
            print(f"{'✓' if form_fields else '✗'} Form fields present: {len(form_fields) if form_fields else 0}")
            print(f"{'✓' if not errors else '✗'} No errors: {len(errors) == 0}")
            print("=" * 80)
            
        except Exception as e:
            print(f"\n❌ ERROR: {str(e)}")
            await page.screenshot(path='/home/cypress/alethicode/screenshot_error.png')
            print(f"Error screenshot saved: screenshot_error.png")
            
        finally:
            await browser.close()

if __name__ == '__main__':
    asyncio.run(test_profile_page())
