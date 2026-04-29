const fs = require('fs');
const path = require('path');

const files = [
    'update_superadmin_phone_correct.js',
    'update_superadmin_phone.js',
    'revert_to_admin.js',
    'link_unlinked_publishers.js',
    'fix_user.js',
    'check_user.js',
    'check_testing_publishers.js',
    'check_attachments.js'
];

const uriPattern = 'mongodb+srv://ruthvikg00_db_user:c4k51DIu0Rolx6ne@cluster0.ynrgzwo.mongodb.net/mobilize';
const placeholder = 'YOUR_MONGODB_URI_HERE';

files.forEach(file => {
    const filePath = path.join(__dirname, file);
    if (fs.existsSync(filePath)) {
        let content = fs.readFileSync(filePath, 'utf8');
        content = content.split(uriPattern).join(placeholder);
        fs.writeFileSync(filePath, content);
        console.log(`Masked ${file}`);
    }
});
