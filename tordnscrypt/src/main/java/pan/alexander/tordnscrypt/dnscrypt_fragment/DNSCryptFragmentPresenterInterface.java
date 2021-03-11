package pan.alexander.tordnscrypt.dnscrypt_fragment;

/*
    This file is part of InviZible Pro.

    InviZible Pro is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    InviZible Pro is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with InviZible Pro.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2019-2021 by Garmatin Oleksandr invizible.soft@gmail.com
*/

public interface DNSCryptFragmentPresenterInterface {
    boolean isDNSCryptInstalled();

    boolean isSavedDNSStatusRunning();

    void saveDNSStatusRunning(boolean running);

    void displayLog(boolean modulesStateChangingExpected);

    void stopDisplayLog();

    void refreshDNSCryptState();

    void setDnsCryptSomethingWrong();

    void setDnsCryptRunning();

    void setDnsCryptStopped();

    void setDnsCryptInstalling();

    void setDnsCryptInstalled();

    void setDNSCryptStartButtonEnabled(boolean enabled);

    void setDNSCryptProgressBarIndeterminate(boolean indeterminate);
}