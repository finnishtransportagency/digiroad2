(function (root) {
    /**
     * Removes scandic characters and replaces spaces with -
     * i.e. "Linja- ja kuorma-autojen pysäköintialue" => "linja-ja-kuorma-autojen-pysakointialue"
     */
    root.toDataValue = function (text) {
        return text.trim().toLowerCase()
            .replace(/ä/g, 'a')
            .replace(/ö/g, 'o')
            .replace(/å/g, 'a')
            .replace(/ /g, '-')
            .replace(/--/g, '-');
    };
})(window.TextUtils = window.TextUtils || {});