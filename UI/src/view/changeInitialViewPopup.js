window.ChangeInitialViewPopup = function(location, userConfig, municipalities) {

    var options = {
        confirmButton: 'Tallenna',
        cancelButton: 'Peruuta',
        successCallback: function(){
        alert("confirmed");
        },
        closeCallback: function(){},
        container: '.container'
    };

    var municipalitiesOptions = function (municipality) {
        return '' +
            '<option value=municipality.id>' +
            municipality.name +
            '</option>';
    };

    var municipalityDropdown = function () {
        return _.map(municipalities, function(municipality) {
            var x = 1;
            return municipalitiesOptions(municipality).concat('');
        });
    };

    var confirmDiv =
        '<div class="modal-overlay confirm-modal">' +
            '<div class="modal-dialog">' +
                '<div class="content">' +
                  "Aloitussivu" +
                '</div>' +

                '<div class="contentNoBackColor">' +
                    '<span class="column1">' +
                        '<p>' + "Aloitussijainti" + '</p>' +
                    '</span>' +
                    '<span class="column2">' +
                        '<p>' + "Default location" + '</p>' +
                    '<select name="municipalities">' +
                        municipalityDropdown() +
                    '</select>' +
                        '<p>' + "Dropdown" + '</p>' +
                    '</span>' +
                '</div>' +

                /*'<div class="contentNoBackColor">' +
                    '<p style="float: left">' + "Asset Type" + '</p>' +
                    '<p style="float: right">' + "Right" + "Wing" + '</p>' +
                    '<br>' +
                    '<p style="float: right">' + "Dropdown" + '</p>' +
                    '</div>' +*/

                '<br>' +

                '<div class="actions" style ="float: right">' +
                    '<button class = "btn btn-secondary no">' + options.cancelButton + '</button>' +
                    '<button class = "btn btn-primary yes">' + options.confirmButton + '</button>' +
                '</div>' +
            '</div>' +
        '</div>';

    var renderConfirmDialog = function() {
        var template = confirmDiv;

        jQuery(options.container).append(template);
        var modal = $('.modal-dialog');
    };

    var bindEvents = function() {
        jQuery('.confirm-modal .no').on('click', function() {
            purge();
            options.closeCallback();
        });
        jQuery('.confirm-modal .yes').on('click', function() {
            purge();
            options.successCallback();
        });
    };

    var show = function() {
        purge();
        renderConfirmDialog();
        bindEvents();
    };

    var purge = function() {
        jQuery('.confirm-modal').remove();
    };

    show();
};