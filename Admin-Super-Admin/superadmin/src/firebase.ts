import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
    apiKey: "AIzaSyBqyzKhjxdIw4mp9WL2VPEH0TXJq35ikZA",
    authDomain: "keliri-47a3e.firebaseapp.com",
    projectId: "keliri-47a3e",
    storageBucket: "keliri-47a3e.firebasestorage.app",
    messagingSenderId: "946979464342",
    appId: "1:946979464342:web:8f53055eb0c84e27c64eb7",
    measurementId: "G-337GKXH980"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
auth.languageCode = 'en';

export default app;
