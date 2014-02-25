(function(session, undefined){
    session.redirectToLogin = function() {
        window.location = "index.html";
    };

}(window.session = window.session || {}));

$(document).ajaxComplete(function(event, jqXHR, ajaxSettings) {
    var digiroadResponse = jqXHR.getResponseHeader("Digiroad2-Server-Originated-Response");
    if (!digiroadResponse) {
//        session.redirectToLogin();
    }
});