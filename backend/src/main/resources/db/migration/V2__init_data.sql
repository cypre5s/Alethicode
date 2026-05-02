INSERT INTO sys_options(key, value)
VALUES
('website_config', jsonb_build_object(
    'website_base_url', 'http://127.0.0.1',
    'website_name', 'Alethicode',
    'website_name_shortcut', 'AIOJ',
    'website_footer', '',
    'allow_register', true,
    'submission_list_show_all', true
)),
('languages', jsonb_build_object(
    'languages', jsonb_build_array('Python3', 'C', 'C++', 'Java'),
    'spj_languages', jsonb_build_array('C', 'C++')
))
ON CONFLICT (key) DO NOTHING;
