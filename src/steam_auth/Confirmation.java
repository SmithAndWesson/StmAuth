package steam_auth;

public class Confirmation
{
    public String ID;
    public String Key;
    public String Description;
    public String partnerId;
    public int ConfState;
    public String Type;
    public ConfirmationType ConfType;

    public enum ConfirmationType
    {
        GenericConfirmation,
        Trade,
        MarketSellTransaction,
        Unknown
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Confirmation simpson = (Confirmation) o;
        return ID.equals(simpson.ID);
    }

    @Override
    public int hashCode() {
        return this.ID.hashCode();
    }
}
