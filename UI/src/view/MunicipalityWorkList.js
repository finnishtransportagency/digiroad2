(function (root) {
    root.MunicipalityWorkList = function(map, header) {
        var element =
            $('<div class="header-link">' +
                    '<div class="verify-button">Tietolajin kuntasivu</div>' +
                '</div>');

        header.append(element);

        element.find('.verify-button').on('click', function (event) {

            alert('click');

        });
    };
})(this);