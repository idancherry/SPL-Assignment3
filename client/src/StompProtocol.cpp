#include "../include/StompProtocol.h"
#include "sstream"


StompProtocol::StompProtocol() : 
    _isLoggedIn(false),           
    _shouldTerminate(false),      
    _subIdCounter(0),             
    _receiptIdCounter(0){};

std::string StompProtocol::processKeyboardInput(std::string input) {
    // 1. פרקי את הקלט למילים (למשל join, sci-fi)
    // 2. השתמשי ב-switch או if-else על המילה הראשונה
    // 3. בצעי את הלוגיקה (קידום מונים, שמירה במפות)
    // 4. החזירי מחרוזת STOMP מלאה

    std::string ans="";
    return ans;
}

void StompProtocol::processServerResponse(std::string message) {
    // 1. פרקי את ההודעה מהשרת לשורות
    // 2. זהי אם זה MESSAGE / ERROR / RECEIPT / CONNECTED
    // 3. הדפיסי למשתמש את מה שהוא צריך לראות
}    